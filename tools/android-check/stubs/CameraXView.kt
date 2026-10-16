package androidx.camera.view

import androidx.camera.core.MeteringPointFactory
import androidx.camera.core.Preview
import androidx.camera.core.ViewPort

class PreviewView(context: android.content.Context) : android.widget.FrameLayout(context) {
    enum class ImplementationMode { PERFORMANCE, COMPATIBLE }
    enum class ScaleType { FILL_START, FILL_CENTER, FILL_END, FIT_START, FIT_CENTER, FIT_END }

    var implementationMode: ImplementationMode = ImplementationMode.COMPATIBLE
    var scaleType: ScaleType = ScaleType.FILL_CENTER
    val surfaceProvider: Preview.SurfaceProvider get() = Preview.SurfaceProvider { }
    val viewPort: ViewPort? get() = null
    val meteringPointFactory: MeteringPointFactory get() = object : MeteringPointFactory() {}
}
