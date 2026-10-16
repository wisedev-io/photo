package app.dvphoto.camera

import app.dvphoto.core.RawFace
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object MlKit {
    fun options(accurate: Boolean): FaceDetectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(
            if (accurate) FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE else FaceDetectorOptions.PERFORMANCE_MODE_FAST
        )
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
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
}

suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}
