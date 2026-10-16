package app.dvphoto.imaging

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import app.dvphoto.core.DvRules
import app.dvphoto.core.PixelSource
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Bitmap adapter for the shared scene maths. Bitmap must be a software bitmap. */
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
    /** Encodes a square bitmap as a metadata-free JPEG within the DV size limits. */
    fun encode(src: Bitmap): Encoded {
        val srcSide = min(src.width, src.height)
        if (srcSide < DvRules.MIN_SIDE_PX) return Encoded(compress(src, 90), srcSide) // fails the resolution check anyway
        var side = min(srcSide, DvRules.OUTPUT_TARGET_SIDE)
        while (true) {
            val bmp = if (side == src.width && src.width == src.height) src
            else Bitmap.createScaledBitmap(src, side, side, true)
            val bytes = bestQuality(bmp)
            if (bmp !== src) bmp.recycle()
            if (bytes != null) return Encoded(bytes, side)
            if (side <= DvRules.MIN_SIDE_PX) break
            side = max(DvRules.MIN_SIDE_PX, (side * 0.9f).roundToInt())
        }
        val last = Bitmap.createScaledBitmap(src, DvRules.MIN_SIDE_PX, DvRules.MIN_SIDE_PX, true)
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

    fun saveToGallery(context: Context, bytes: ByteArray): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "dv_photo_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DVPhoto")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("no stream")
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            null
        }
    }
}

/** Decodes a picked image, applying EXIF rotation and capping the long side to keep memory low. */
fun decodeBounded(context: Context, uri: Uri, maxSide: Int = 2000): Bitmap? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = max(info.size.width, info.size.height)
        if (longest > maxSide) {
            val s = maxSide.toFloat() / longest
            decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
        }
    }
}.getOrNull()
