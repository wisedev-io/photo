package androidx.camera.camera2.interop

import android.hardware.camera2.CaptureRequest
import androidx.camera.core.ExtendableBuilder

// a Java annotation in the real library: Kotlin does not enforce it, Android lint does
annotation class ExperimentalCamera2Interop

class Camera2Interop private constructor() {
    @ExperimentalCamera2Interop
    class Extender<T>(baseBuilder: ExtendableBuilder<T>) {
        fun <ValueT> setCaptureRequestOption(key: CaptureRequest.Key<ValueT>, value: ValueT): Extender<T> = this
    }
}
