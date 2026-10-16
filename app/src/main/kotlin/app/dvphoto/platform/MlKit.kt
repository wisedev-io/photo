package app.dvphoto.platform

import app.dvphoto.core.RawFace
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * ML Kit face detection (bundled model, works offline). It is used only to MEASURE the face;
 * the pixels of the photo are never altered by it.
 */
object MlKit {
    /** Landmarks, contour and eye/smile probabilities for the main face. */
    fun measureOptions(accurate: Boolean): FaceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(
            if (accurate) FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE else FaceDetectorOptions.PERFORMANCE_MODE_FAST
        )
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .build()

    /**
     * Counts faces. Contour mode only reports one face, so extra people are found with a second,
     * cheaper detector that has no contours.
     */
    fun countOptions(): FaceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .setMinFaceSize(0.08f)
        .build()

    fun largest(faces: List<Face>): Face? =
        faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }

    fun toRaw(face: Face): RawFace? {
        val le = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val re = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null
        val pts = face.getContour(FaceContour.FACE)?.points
        val box = face.boundingBox
        val open = listOfNotNull(face.leftEyeOpenProbability, face.rightEyeOpenProbability).minOrNull()
        return RawFace(
            ovalTop = pts?.minOfOrNull { it.y } ?: box.top.toFloat(),
            chin = pts?.maxOfOrNull { it.y } ?: box.bottom.toFloat(),
            minX = pts?.minOfOrNull { it.x } ?: box.left.toFloat(),
            maxX = pts?.maxOfOrNull { it.x } ?: box.right.toFloat(),
            eyeLeftX = le.x, eyeLeftY = le.y, eyeRightX = re.x, eyeRightY = re.y,
            roll = face.headEulerAngleZ, yaw = face.headEulerAngleY, pitch = face.headEulerAngleX,
            eyesOpen = open,
            smile = face.smilingProbability,
        )
    }

    /** Other faces that are big enough to count as "another person in the photo". */
    fun otherPeople(all: List<Face>, main: Face): Int {
        val mainArea = main.boundingBox.width().toLong() * main.boundingBox.height()
        return all.count { f ->
            val a = f.boundingBox.width().toLong() * f.boundingBox.height()
            // the main face shows up in the second pass too: skip boxes that overlap it heavily
            val overlap = overlapRatio(f, main)
            overlap < 0.5f && a >= mainArea / 6
        }
    }

    private fun overlapRatio(a: Face, b: Face): Float {
        val ra = a.boundingBox
        val rb = b.boundingBox
        val l = maxOf(ra.left, rb.left)
        val t = maxOf(ra.top, rb.top)
        val r = minOf(ra.right, rb.right)
        val bt = minOf(ra.bottom, rb.bottom)
        if (r <= l || bt <= t) return 0f
        val inter = (r - l).toLong() * (bt - t)
        val smaller = minOf(ra.width().toLong() * ra.height(), rb.width().toLong() * rb.height()).coerceAtLeast(1)
        return inter.toFloat() / smaller
    }
}

suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
    addOnCanceledListener { c.cancel() }
}
