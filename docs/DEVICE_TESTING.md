# First run on a real phone: calibration and checklist

Everything that can be verified without a device is verified in CI (rules engine, 9-language rendering, Android code
compiled against stubs). What follows is what **only a phone can tell you**. Do it once per camera generation you care
about, then adjust the constants in `core/src/main/kotlin/app/dvphoto/core/DvRules.kt` and re-run `./gradlew -p core test`.

## 1. Calibrate head measurement (10 minutes)

1. *Settings → Show measurements*. The viewfinder now prints `head 60%m  eyes 63%  x 0 …`
   (`m` = head top measured on the pixels, `e` = estimated from the face contour).
2. Stand in front of a plain white wall, take a photo that the app accepts, and save it.
3. Print it at 2 × 2 in (or open it at 100 % on a PC) and measure with a ruler: chin → top of the hair, and
   bottom edge → eye line. Official targets: head 50–69 %, eyes 56–69 %.
4. Compare with the numbers the app showed. If the head is consistently larger or smaller than reported:
   * mostly `m` readings → fine, nothing to change;
   * mostly `e` readings (white / very light hair, shaved head) → adjust `HEAD_TOP_EXTENSION` (currently 0.20 of the
     face-contour height). Raise it if the real head is taller than reported.
5. Repeat with 3–4 people (different hair, skin tones, glasses off) before trusting the margins.

## 2. Thresholds that depend on the camera

| Constant | Symptom if wrong |
|---|---|
| `FACE_LUMA_MIN/MAX`, `MAX_LIGHT_ASYMMETRY` | good light rejected / bad light accepted (especially dark skin tones) |
| `BG_LUMA_MIN`, `BG_STD_MAX`, `BG_OUTLIER_MAX`, `BG_EDGE_*` | a clean white wall rejected, or a shadow / poster accepted |
| `BG_CHROMA_MAX` | warm indoor light makes a white wall look "tinted" |
| `MIN_SHARPNESS` | sharp photos rejected (low-light phones) or blurry ones accepted |
| `MAX_YAW`, `MAX_PITCH`, `MAX_ROLL` | impossible to hold still enough / too forgiving |

## 3. Checklist

Camera
- [ ] Front camera: shapes line up with the face, instructions change as you move, auto-capture fires after ~1.2 s steady
- [ ] Back camera (flip button), someone else holding the phone
- [ ] Switching off *Auto-capture* in Settings → only the button takes the picture
- [ ] *Voice instructions* speak (English, and your language if the TTS engine has it)
- [ ] Permission denied once, then "Don't ask again" → *Open app settings* button appears and works
- [ ] A phone without a front camera / tablet: flip button hidden, no crash
- [ ] Bright window behind you: face is still exposed correctly (metering on the face)
- [ ] Verify "no filters": compare with the stock camera's *beauty / portrait / HDR* modes — this app's picture has none

Upload
- [ ] JPEG from the gallery, HEIC from an iPhone, PNG screenshot, WebP
- [ ] A selfie taken with the stock app in *portrait mode* → warning
- [ ] A photo edited with FaceApp / BeautyPlus / Photoshop → "not allowed" or warning
- [ ] A photo older than 6 months → red "Photo age"; WhatsApp-forwarded photo (no EXIF) → "Date not found" warning
- [ ] A photo with two people → "Only one person in the photo"
- [ ] A tiny face in a big photo → low-resolution message, not a silently upscaled picture

Save
- [ ] Android 14/15: file appears in *Gallery → DVPhoto* and in *Files → Pictures/DVPhoto*
- [ ] Android 7–9: asks for storage permission once, then saves
- [ ] *Downloads* choice → `Download/DVPhoto/`
- [ ] Same name twice → second becomes `name (1).jpg`
- [ ] Cyrillic name → *Use English letters* → `Kamoliddin_Rasulov`
- [ ] *Choose another location…* (system picker) writes the file
- [ ] The saved file is a square JPEG, 600–1200 px, ≤ 240 KB, no EXIF (`exiftool file.jpg`)
- [ ] Upload that file on the real DV entry site and confirm it is accepted

Other
- [ ] Switch language to Arabic → layout mirrors, nothing overlaps
- [ ] Rotate / fold / multi-window → no crash, camera keeps working
- [ ] Android 7.x: launcher icon shows, app starts
