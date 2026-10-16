package androidx.activity.result.contract

abstract class ActivityResultContract<I, O>

class ActivityResultContracts private constructor() {
    class PickVisualMedia : ActivityResultContract<androidx.activity.result.PickVisualMediaRequest, android.net.Uri?>() {
        sealed interface VisualMediaType
        object ImageOnly : VisualMediaType
    }
    class CreateDocument(mimeType: String) : ActivityResultContract<String, android.net.Uri?>()
    class RequestPermission : ActivityResultContract<String, Boolean>()
}
