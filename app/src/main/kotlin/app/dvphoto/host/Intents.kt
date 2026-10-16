package app.dvphoto.host

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import app.dvphoto.R

private fun Context.startSafely(intent: Intent): Boolean = try {
    startActivity(intent)
    true
} catch (e: ActivityNotFoundException) {
    Toast.makeText(this, R.string.err_open, Toast.LENGTH_SHORT).show()
    false
} catch (e: SecurityException) {
    Toast.makeText(this, R.string.err_open, Toast.LENGTH_SHORT).show()
    false
}

fun openImage(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/jpeg").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startSafely(intent)
}

fun shareImage(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startSafely(Intent.createChooser(send, null))
}

fun openUrl(context: Context, url: String) {
    context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

fun openAppSettings(context: Context) {
    context.startSafely(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}
