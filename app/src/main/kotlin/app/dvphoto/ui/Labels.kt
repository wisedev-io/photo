package app.dvphoto.ui

import androidx.annotation.StringRes
import app.dvphoto.R
import app.dvphoto.core.CheckId
import app.dvphoto.core.Hint

@StringRes
fun Hint.res(): Int = when (this) {
    Hint.OK -> R.string.hint_ok
    Hint.NO_FACE -> R.string.hint_no_face
    Hint.MULTIPLE_FACES -> R.string.hint_multiple_faces
    Hint.MOVE_CLOSER -> R.string.hint_move_closer
    Hint.MOVE_BACK -> R.string.hint_move_back
    Hint.MOVE_LEFT -> R.string.hint_move_left
    Hint.MOVE_RIGHT -> R.string.hint_move_right
    Hint.EYES_LOW -> R.string.hint_eyes_low
    Hint.EYES_HIGH -> R.string.hint_eyes_high
    Hint.TILT_HEAD -> R.string.hint_tilt_head
    Hint.TURNED -> R.string.hint_turned
    Hint.CHIN_LEVEL -> R.string.hint_chin_level
    Hint.EYES_CLOSED -> R.string.hint_eyes_closed
    Hint.SMILING -> R.string.hint_smiling
    Hint.TOO_DARK -> R.string.hint_too_dark
    Hint.TOO_BRIGHT -> R.string.hint_too_bright
    Hint.UNEVEN_LIGHT -> R.string.hint_uneven_light
    Hint.NOT_LIGHT -> R.string.hint_not_light
    Hint.NOT_PLAIN -> R.string.hint_not_plain
    Hint.NOT_NEUTRAL -> R.string.hint_not_neutral
    Hint.BLURRY -> R.string.hint_blurry
    Hint.LOW_RES -> R.string.hint_low_res
    Hint.FILE_TOO_LARGE -> R.string.hint_file_too_large
    Hint.FILE_SIZE_RANGE -> R.string.hint_file_size_range
}

@StringRes
fun CheckId.res(): Int = when (this) {
    CheckId.FACE -> R.string.check_face
    CheckId.HEAD_SIZE -> R.string.check_head_size
    CheckId.CENTER -> R.string.check_center
    CheckId.EYE_LINE -> R.string.check_eye_line
    CheckId.POSE -> R.string.check_pose
    CheckId.EYES_OPEN -> R.string.check_eyes_open
    CheckId.EXPRESSION -> R.string.check_expression
    CheckId.LIGHTING -> R.string.check_lighting
    CheckId.BACKGROUND -> R.string.check_background
    CheckId.SHARPNESS -> R.string.check_sharpness
    CheckId.RESOLUTION -> R.string.check_resolution
    CheckId.FILE -> R.string.check_file
}

/** The five chips shown under the viewfinder. */
enum class Group(@StringRes val label: Int, val ids: Set<CheckId>) {
    FACE(R.string.group_face, setOf(CheckId.FACE)),
    FRAMING(R.string.group_framing, setOf(CheckId.HEAD_SIZE, CheckId.CENTER, CheckId.EYE_LINE)),
    POSE(R.string.group_pose, setOf(CheckId.POSE, CheckId.EYES_OPEN, CheckId.EXPRESSION)),
    LIGHT(R.string.group_light, setOf(CheckId.LIGHTING)),
    BACKGROUND(R.string.group_background, setOf(CheckId.BACKGROUND)),
}
