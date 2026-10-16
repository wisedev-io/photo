package com.google.mlkit.vision.common

class InputImage private constructor() {
    companion object {
        @JvmStatic fun fromMediaImage(image: android.media.Image, rotationDegrees: Int): InputImage = InputImage()
        @JvmStatic fun fromBitmap(bitmap: android.graphics.Bitmap, rotationDegrees: Int): InputImage = InputImage()
    }
}
