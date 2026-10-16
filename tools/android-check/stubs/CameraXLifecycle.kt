package androidx.camera.lifecycle

import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.UseCaseGroup
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture

class ProcessCameraProvider private constructor() {
    fun hasCamera(selector: CameraSelector): Boolean = true
    fun unbindAll() {}
    fun bindToLifecycle(owner: LifecycleOwner, selector: CameraSelector, group: UseCaseGroup): Camera = error("stub")

    companion object {
        @JvmStatic fun getInstance(context: android.content.Context): ListenableFuture<ProcessCameraProvider> = error("stub")
    }
}
