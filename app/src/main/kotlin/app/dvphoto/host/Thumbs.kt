package app.dvphoto.host

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.dvphoto.ui.Pal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Small gallery thumbnail, decoded off the main thread. Border colour shows whether the photo passed. */
@Composable
fun RecentThumb(uri: String, passed: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { loadThumbnail(ctx, Uri.parse(uri), 240)?.asImageBitmap() }
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier.clip(shape).background(Pal.Surface2).border(2.dp, if (passed) Pal.Mint.copy(alpha = 0.7f) else Pal.Coral.copy(alpha = 0.7f), shape),
    ) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
    }
}

private fun loadThumbnail(context: Context, uri: Uri, px: Int): Bitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(uri, Size(px, px), null)
    else decodeSampled(context, uri, px)
}.getOrNull()

private fun decodeSampled(context: Context, uri: Uri, px: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= px) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}
