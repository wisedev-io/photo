package app.dvphoto.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import app.dvphoto.core.CivilDate
import app.dvphoto.core.Dates
import app.dvphoto.core.DvRules
import app.dvphoto.core.MetadataScanner
import app.dvphoto.core.PixelSource
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Bitmap adapter for the shared scene maths. The bitmap must be a software bitmap. */
class BitmapSource(private val b: Bitmap) : PixelSource {
    override val width get() = b.width
    override val height get() = b.height
    override fun luma(x: Int, y: Int): Int {
        val p = b.getPixel(x, y)
        return (299 * Color.red(p) + 587 * Color.green(p) + 114 * Color.blue(p)) / 1000
    }
    override fun chroma(x: Int, y: Int): Int {
        val p = b.getPixel(x, y)
        val r = Color.red(p); val g = Color.green(p); val bl = Color.blue(p)
        return max(r, max(g, bl)) - min(r, min(g, bl))
    }
}

class Encoded(val bytes: ByteArray, val side: Int)

object PhotoExporter {
    /**
     * Copies [side] x [side] pixels starting at ([left], [top]) of [src] into a brand-new sRGB bitmap of
     * [outSide] pixels. Crop and resize are the only things done to the picture - never any filtering or
     * colour work. Drawing into a fresh bitmap also drops colour profiles and HDR gain maps.
     */
    fun renderSquare(src: Bitmap, left: Int, top: Int, side: Int, outSide: Int): Bitmap {
        val s = min(side, min(src.width, src.height)).coerceAtLeast(1)
        val l = left.coerceIn(0, src.width - s)
        val t = top.coerceIn(0, src.height - s)
        val out = Bitmap.createBitmap(outSide, outSide, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE) // transparent PNGs end up on white
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, Rect(l, t, l + s, t + s), Rect(0, 0, outSide, outSide), paint)
        return out
    }

    /** Output side for a crop of [cropSide] pixels: as large as allowed up to the target, never upscaled. */
    fun outputSide(cropSide: Int): Int = min(cropSide, DvRules.OUTPUT_TARGET_SIDE).coerceAtLeast(1)

    /** Encodes a square bitmap as a metadata-free JPEG within the DV size limits (shrinking only if needed). */
    fun encode(square: Bitmap): Encoded {
        val srcSide = min(square.width, square.height)
        if (srcSide < DvRules.MIN_SIDE_PX) return Encoded(compress(square, 90), srcSide) // fails the resolution check anyway
        var side = min(srcSide, DvRules.OUTPUT_TARGET_SIDE)
        while (true) {
            val bmp = if (side == square.width && square.width == square.height) square
            else renderSquare(square, 0, 0, srcSide, side)
            val bytes = bestQuality(bmp)
            if (bmp !== square) bmp.recycle()
            if (bytes != null) return Encoded(bytes, side)
            if (side <= DvRules.MIN_SIDE_PX) break
            side = max(DvRules.MIN_SIDE_PX, (side * 0.9f).roundToInt())
        }
        val last = renderSquare(square, 0, 0, srcSide, DvRules.MIN_SIDE_PX)
        return Encoded(compress(last, 40), DvRules.MIN_SIDE_PX).also { last.recycle() }
    }

    private fun bestQuality(bmp: Bitmap): ByteArray? {
        var lo = 60; var hi = 95; var best: ByteArray? = null
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            val b = compress(bmp, mid)
            if (b.size <= DvRules.MAX_FILE_BYTES) { best = b; lo = mid + 1 } else hi = mid - 1
        }
        return best
    }

    private fun compress(bmp: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream(220 * 1024)
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}

/** Decodes a picked image to sRGB, applies its EXIF rotation, and caps the long side to keep memory low. */
fun decodeBounded(context: Context, uri: Uri, maxSide: Int = 2000): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 28) decodeWithImageDecoder(context, uri, maxSide) else decodeLegacy(context, uri, maxSide)
}.getOrNull()

@androidx.annotation.RequiresApi(28)
private fun decodeWithImageDecoder(context: Context, uri: Uri, maxSide: Int): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        val longest = max(info.size.width, info.size.height)
        if (longest > maxSide) {
            val s = maxSide.toFloat() / longest
            decoder.setTargetSize((info.size.width * s).roundToInt().coerceAtLeast(1), (info.size.height * s).roundToInt().coerceAtLeast(1))
        }
    }
}

private fun decodeLegacy(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
        if (Build.VERSION.SDK_INT >= 26) inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
    }
    var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
    val orientation = resolver.openInputStream(uri)?.use {
        ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } ?: ExifInterface.ORIENTATION_NORMAL
    val m = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
        else -> Unit
    }
    if (!m.isIdentity) {
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (rotated !== bmp) bmp.recycle()
        bmp = rotated
    }
    return bmp
}

/** First bytes of the picked file: enough to read every metadata segment, never the pixels. */
fun readHeader(context: Context, uri: Uri, max: Int = MetadataScanner.MAX_SCAN_BYTES): ByteArray = runCatching {
    context.contentResolver.openInputStream(uri)?.use { s ->
        val buf = ByteArray(max)
        var n = 0
        while (n < max) {
            val r = s.read(buf, n, max - n)
            if (r <= 0) break
            n += r
        }
        buf.copyOf(n)
    } ?: ByteArray(0)
}.getOrDefault(ByteArray(0))

class ExifExtras(val software: String?, val takenAt: CivilDate?)

/** Tags read by the platform's own EXIF reader; covers HEIC, PNG and WebP where our JPEG parser sees nothing. */
fun readExifExtras(context: Context, uri: Uri): ExifExtras = runCatching {
    context.contentResolver.openInputStream(uri)?.use { s ->
        val e = ExifInterface(s)
        ExifExtras(
            e.getAttribute(ExifInterface.TAG_SOFTWARE),
            Dates.parseExifDate(e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: e.getAttribute(ExifInterface.TAG_DATETIME)),
        )
    }
}.getOrNull() ?: ExifExtras(null, null)
