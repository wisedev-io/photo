# First run on a real phone: calibration and checklist

Everything that can be verified without a device is verified in CI (rules engine, 13-language rendering, Android code
compiled against stubs). What follows is what **only a phone can tell you**. Do it once per camera generation you care
about, then adjust the constants in `core/src/main/kotlin/app/dvphoto/core/Strictness.kt` (the limits per level;
`HEAD_TOP_EXTENSION` stays in `DvRules.kt`) and re-run `./gradlew -p core test`.

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

Each is a field of `Thresholds` in `Strictness.kt`, one value per level. Calibrate **Standard** first, then check the
other two against it (see the strictness checklist below).

| Field | Symptom if wrong |
|---|---|
| `faceLumaMin/Max`, `maxLightAsymmetry` | good light rejected / bad light accepted (especially dark skin tones) |
| `bgLumaMin`, `bgStdMax`, `bgOutlierMax`, `bgEdgeMax` | a clean white wall rejected, or a shadow / poster accepted |
| `bgChromaMax` | warm indoor light makes a white wall look "tinted" |
| `minSharpness` | sharp photos rejected (low-light phones) or blurry ones accepted. Photos are analysed at 600 × 600, where edges are steeper than in a larger picture — expect to raise it |
| `maxYaw`, `maxPitch`, `maxRoll` | impossible to hold still enough / too forgiving |
| `holdMs`, `steadyTolerance` | auto-capture never fires (too tight) or fires while you are still moving |

## 3. Checklist

Strictness (Settings → Strictness)
- [ ] *Standard*: a careful photo in good light against a white wall is accepted within a few tries
- [ ] *Strict*: the same careful photo can still be accepted (if auto-capture never fires, loosen `Strict` in `Strictness.kt`)
- [ ] *Relaxed*: accepts photos at the official limits (head 50 % or 69 %), and nothing the official numbers forbid
- [ ] Take a photo that fails at *Standard* only by a little: the result screen lists the looser level it would pass at
- [ ] Open *Help → Where the rules come from*: every row opens its State Department page in the browser
- [ ] Set the phone's date 11+ months after `DvRules.RULES_AS_OF`: a yellow "rules may have changed" line appears in Settings and Help

Camera
- [ ] Front camera: shapes line up with the face, instructions change as you move, auto-capture fires after ~1.2 s steady (Standard)
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
- [ ] Switch language to Arabic and to Persian → layout mirrors, nothing overlaps
- [ ] Switch to Nepali and Amharic → text renders (no empty squares) and nothing is cut off
- [ ] Voice instructions in Ukrainian / Persian / Nepali / Amharic: the TTS engine may lack a voice — the app must stay silent or fall back, never crash
- [ ] Rotate / fold / multi-window → no crash, camera keeps working
- [ ] Android 7.x: launcher icon shows, app starts
