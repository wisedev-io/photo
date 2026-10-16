package app.dvphoto.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import app.dvphoto.R
import app.dvphoto.core.Check
import app.dvphoto.core.CheckId
import app.dvphoto.core.Hint
import app.dvphoto.core.Status
import kotlin.math.roundToInt

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
    Hint.EDIT_AI -> R.string.hint_edit_ai
    Hint.EDIT_APP -> R.string.hint_edit_app
    Hint.EDIT_CREDENTIALS -> R.string.hint_edit_credentials
    Hint.EDIT_PORTRAIT -> R.string.hint_edit_portrait
    Hint.EDIT_SOFTWARE -> R.string.hint_edit_software
    Hint.AGE_OLD -> R.string.hint_age_old
    Hint.AGE_UNKNOWN -> R.string.hint_age_unknown
    Hint.AGE_FUTURE -> R.string.hint_age_future
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
    CheckId.EDITING -> R.string.check_editing
    CheckId.PHOTO_AGE -> R.string.check_photo_age
    CheckId.FILE -> R.string.check_file
}

/** Instruction / explanation text of a check; the age hint carries the number of months. */
@Composable
fun Check.hintText(): String =
    if (hint == Hint.AGE_OLD) stringResource(R.string.hint_age_old, (value ?: 0f).roundToInt())
    else stringResource(hint.res())

/** Short measurement shown next to a check, e.g. "58%" for the head size; null when there is nothing to show. */
fun Check.valueText(): String? = when (id) {
    CheckId.HEAD_SIZE, CheckId.EYE_LINE -> value?.let { "${(it * 100).roundToInt()}%" }
    else -> null
}

fun Status.color(): Color = when (this) {
    Status.PASS -> Pal.Mint
    Status.WARN -> Pal.Amber
    Status.FAIL -> Pal.Coral
}

/** The five chips shown under the viewfinder. */
enum class Group(@StringRes val label: Int, val ids: Set<CheckId>) {
    FACE(R.string.group_face, setOf(CheckId.FACE)),
    FRAMING(R.string.group_framing, setOf(CheckId.HEAD_SIZE, CheckId.CENTER, CheckId.EYE_LINE)),
    POSE(R.string.group_pose, setOf(CheckId.POSE, CheckId.EYES_OPEN, CheckId.EXPRESSION)),
    LIGHT(R.string.group_light, setOf(CheckId.LIGHTING)),
    BACKGROUND(R.string.group_background, setOf(CheckId.BACKGROUND)),
}
