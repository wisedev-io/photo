package com.google.mlkit.vision.face

import android.graphics.PointF
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage

class FaceLandmark {
    val position: PointF = PointF()
    companion object { const val LEFT_EYE = 4; const val RIGHT_EYE = 10 }
}

class FaceContour {
    val points: List<PointF> = emptyList()
    companion object { const val FACE = 1 }
}

class Face {
    val boundingBox: Rect = Rect()
    val headEulerAngleX: Float = 0f
    val headEulerAngleY: Float = 0f
    val headEulerAngleZ: Float = 0f
    val leftEyeOpenProbability: Float? = null
    val rightEyeOpenProbability: Float? = null
    val smilingProbability: Float? = null
    fun getLandmark(type: Int): FaceLandmark? = null
    fun getContour(type: Int): FaceContour? = null
}

class FaceDetectorOptions private constructor() {
    class Builder {
        fun setPerformanceMode(mode: Int): Builder = this
        fun setLandmarkMode(mode: Int): Builder = this
        fun setClassificationMode(mode: Int): Builder = this
        fun setContourMode(mode: Int): Builder = this
        fun setMinFaceSize(size: Float): Builder = this
        fun build(): FaceDetectorOptions = FaceDetectorOptions()
    }
    companion object {
        const val PERFORMANCE_MODE_FAST = 1
        const val PERFORMANCE_MODE_ACCURATE = 2
        const val LANDMARK_MODE_NONE = 1
        const val LANDMARK_MODE_ALL = 2
        const val CLASSIFICATION_MODE_NONE = 1
        const val CLASSIFICATION_MODE_ALL = 2
        const val CONTOUR_MODE_NONE = 1
        const val CONTOUR_MODE_ALL = 2
    }
}

interface FaceDetector : java.io.Closeable {
    fun process(image: InputImage): Task<List<Face>>
}

object FaceDetection { fun getClient(options: FaceDetectorOptions): FaceDetector = error("stub") }
