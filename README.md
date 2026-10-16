# DV Photo Camera

Android app (Kotlin, Jetpack Compose, CameraX, ML Kit) that helps you take or check a photo for the
**U.S. Diversity Visa (DV) Lottery** and refuses to call it "ready" unless it meets the rules.

Everything runs on the device. No account, no upload, no backend. Not affiliated with the U.S. government.

## What it does

- **Guided camera** - a square viewfinder with on-screen shapes: target head oval, shoulder guide,
  allowed eye-line band and centre ticks. Your detected head is drawn in white; line it up with the
  coloured target.
- **Plain-language instructions** - one instruction at a time ("Move closer", "Look straight at the
  camera", "Shadow on your face"), plus five status chips (Face, Framing, Pose, Light, Background).
- **Auto-capture** - fires after everything has been green and steady for 1.2 s. Manual shutter too.
- **Upload & check** - pick any photo; it is auto-cropped around the face to the ideal framing and every
  rule is reported. Bad photos get a red verdict and a retake prompt ("Save anyway" is a quiet, explicit option).
- **Exports a compliant file** - square, 600-1200 px, JPEG <= 240 KB (quality is searched to fit),
  metadata-free, saved to `Pictures/DVPhoto`.
- **History** - saved photos are recorded in a local Room database (`dvphoto.db`) and shown on the home screen.

## Rules enforced (see `core/.../DvRules.kt`)

| Rule | Value |
|---|---|
| Image | square, 600-1200 px, JPEG, <= 240 KB |
| Head height | 50-69% of image height (we insist on 52-67%) |
| Eye line | 56-69% from the bottom (we insist on 58-67%) |
| Pose | roll <= 5 deg, yaw <= 8 deg, pitch <= 10 deg, eyes open, neutral expression |
| Light | face not too dark/bright, left/right balance (no half-face shadows) |
| Background | light (>= 170/255), uniform, neutral colour |
| Sharpness | Laplacian-variance check on the face |

**Verify these against the current State Department instructions before each registration period.**
All thresholds live in one file so a rule change is a one-file edit.

Known limits: glasses/hats/filters/age of photo cannot be detected - the app asks the user up-front
instead. Head-top is estimated from the face contour (ML Kit gives no hair line), so the head-size check is
approximate and deliberately uses a margin inside the official band. Tune thresholds on real devices.

## Architecture

```
core/   pure Kotlin (no Android): rules, validator, scene maths, smart crop  <- unit tested on the JVM
app/    Android: CameraX + ML Kit (LiveAnalyzer), Compose UI, Room, JPEG exporter
```

Memory: live analysis reads only the camera's Y plane at ~6 fps (no per-frame bitmaps), uploads are
decoded down to 2000 px, a single JPEG is held at a time. Min SDK 29.

## Build

```
gradle :core:test          # rules engine tests (JVM)
gradle :app:assembleDebug  # needs the Android SDK
```

Open the folder in Android Studio (Ladybug or newer). CI is in `.github/workflows/android.yml`.
