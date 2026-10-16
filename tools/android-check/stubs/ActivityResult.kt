package androidx.activity.result

open class ActivityResultLauncher<I> { open fun launch(input: I) {} }

class PickVisualMediaRequest

fun PickVisualMediaRequest(mediaType: androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.VisualMediaType): PickVisualMediaRequest =
    PickVisualMediaRequest()
