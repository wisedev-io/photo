package app.dvphoto.platform

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import app.dvphoto.core.FileNaming
import app.dvphoto.ui.SaveFolder
import java.io.File

class SavedFile(val uri: Uri, val displayName: String, val location: String)

/**
 * Saves finished photos to the phone's public storage.
 *  - Android 10+: through MediaStore into Pictures/DVPhoto or Download/DVPhoto (no permission needed).
 *  - Android 7-9: straight into the same folders (needs WRITE_EXTERNAL_STORAGE) and registered with MediaStore.
 */
object PhotoStorage {
    private const val SUBDIR = "DVPhoto"

    fun relativePath(folder: SaveFolder): String = when (folder) {
        SaveFolder.GALLERY -> "Pictures/$SUBDIR"
        SaveFolder.DOWNLOADS -> "Download/$SUBDIR"
    }

    /** True when saving needs the legacy storage permission (Android 9 and older). */
    fun needsLegacyPermission(): Boolean = Build.VERSION.SDK_INT < 29

    /** File names already present in the target folder (lower-case not applied; compare case-insensitively). */
    fun existingNames(context: Context, folder: SaveFolder): Set<String> = runCatching {
        if (Build.VERSION.SDK_INT >= 29) queryNames(context, folder) else listLegacy(folder)
    }.getOrDefault(emptySet())

    private fun collection(folder: SaveFolder): Uri =
        if (folder == SaveFolder.DOWNLOADS && Build.VERSION.SDK_INT >= 29) downloadsCollection()
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    @androidx.annotation.RequiresApi(29)
    private fun downloadsCollection(): Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI

    @androidx.annotation.RequiresApi(29)
    private fun queryNames(context: Context, folder: SaveFolder): Set<String> {
        val names = LinkedHashSet<String>()
        val rel = relativePath(folder)
        context.contentResolver.query(
            collection(folder),
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf("$rel%"),
            null,
        )?.use { c ->
            val i = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            while (c.moveToNext()) c.getString(i)?.let { names += it }
        }
        return names
    }

    @Suppress("DEPRECATION")
    private fun legacyDir(folder: SaveFolder): File {
        val type = if (folder == SaveFolder.GALLERY) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
        return File(Environment.getExternalStoragePublicDirectory(type), SUBDIR)
    }

    private fun listLegacy(folder: SaveFolder): Set<String> = legacyDir(folder).list()?.toSet() ?: emptySet()

    /**
     * Writes [bytes] as "<baseName>.jpg" (made unique if needed) and returns where it ended up.
     * Throws if the system refuses; callers show an error.
     */
    fun save(context: Context, bytes: ByteArray, baseName: String, folder: SaveFolder): SavedFile {
        val unique = FileNaming.unique(baseName, existingNames(context, folder))
        val fileName = "$unique.${FileNaming.EXTENSION}"
        return if (Build.VERSION.SDK_INT >= 29) saveScoped(context, bytes, fileName, folder)
        else saveLegacy(context, bytes, fileName, folder)
    }

    @androidx.annotation.RequiresApi(29)
    private fun saveScoped(context: Context, bytes: ByteArray, fileName: String, folder: SaveFolder): SavedFile {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(folder))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection(folder), values) ?: error("MediaStore refused the insert")
        try {
            resolver.openOutputStream(uri, "w")?.use { it.write(bytes) } ?: error("No output stream")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        // The system may have renamed the file to avoid a clash; report the real name.
        val actual = displayNameOf(context, uri) ?: fileName
        return SavedFile(uri, actual, "${relativePath(folder)}/$actual")
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, bytes: ByteArray, fileName: String, folder: SaveFolder): SavedFile {
        val dir = legacyDir(folder)
        if (!dir.exists() && !dir.mkdirs()) error("Cannot create ${dir.path}")
        val file = File(dir, fileName)
        file.outputStream().use { it.write(bytes) }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.DATA, file.absolutePath)
            put(MediaStore.Images.Media.SIZE, bytes.size.toLong())
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: Uri.fromFile(file)
        return SavedFile(uri, fileName, "${relativePath(folder)}/$fileName")
    }

    /** Writes into a document the user picked with the system file picker. */
    fun writeTo(context: Context, uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(bytes) } ?: error("No output stream")
    }

    fun displayNameOf(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
