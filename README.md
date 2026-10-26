# 4ENote

A native Android handwriting note-taking app for tablets, built for personal
study use. Open source (GPL-3.0).

## Download

[**⬇ Download the latest APK**](https://github.com/GerkoOo/4ENote/releases/latest/download/4ENote.apk)
· [all releases](https://github.com/GerkoOo/4ENote/releases)

Open the file on your Android tablet (Android 8 or newer) and allow your browser
to install apps when asked. This is a beta. Google Drive sync works for test
users while the Google sign-in is being verified; OneDrive is not ready yet.

To check that an APK really is 4ENote's, compare its signing certificate with this SHA-256
fingerprint (for example with `apksigner verify --print-certs 4ENote.apk`):

```
D1:69:AD:59:9D:60:94:B7:F1:1E:EF:FD:91:3F:4C:95:3D:B5:1B:C6:17:55:C4:A3:FA:E3:E2:DD:D2:EB:27:1A
```

Releases up to 0.3.0 were signed with a development key; 0.4.0 and later use this one, so
moving from 0.3.0 to 0.4.0 means uninstalling first (make a 4ENote backup before).

Website: [gerkooo.github.io/4ENote](https://gerkooo.github.io/4ENote/)

## Features

- Pen, ink pen, calligraphy (pointed or flat nib) and highlighter; eraser, lasso and layers
- Hold a drawn line, circle, triangle or rectangle to straighten it; tables; text boxes with LaTeX
- Handwriting to editable text (English only for now: the open TrOCR model reads English
  handwriting; German and other languages are not recognised yet), and handwritten maths to
  LaTeX (offline, open-source models)
- Pinch to zoom, twist to turn a page, full-screen writing; Arrange pages to reorder them
- Import PDFs and images (with dark-mode colour inversion), scan documents
  (page edges, straightening, filters including inverted), export and share as PDF
- Photo frames filled from your phone, scans for later; focus (Pomodoro) timer
- Optional Google Drive sync (only 4ENote's own folder), with a PDF copy of each note
- Lined, grid, dotted or blank paper; dark mode
- Quick pen dial, pen side buttons, scribble or strike out to erase, finger gestures

## Stack

- Kotlin, Jetpack Compose (Material 3), Room
- Only open-source libraries, no Google services and no tracking: PdfBox-Android
  for PDF export, ONNX Runtime running the open TrOCR (handwriting, English) and
  Pix2Text (maths) models on the device, JLaTeXMath for typesetting, CameraX for
  the scanner's camera; scribble-to-erase, shape recognition and the scanner's
  straightening and clean-up are 4ENote's own code

## Building

```
export JAVA_HOME=/path/to/jdk-17-or-newer   # must be a full JDK, not a JRE
./gradlew assembleDebug
```

Requires the Android SDK with platform 37 and build-tools 37.0.0 installed,
and `local.properties` pointing `sdk.dir` at your SDK (not committed).

## Privacy

No accounts, no tracking, no servers of our own: notes stay on your device and in your own
cloud. See the [privacy policy](PRIVACY.md).

## License

4ENote is licensed under the [GNU General Public License v3.0](LICENSE).
You may use, study, change and share it. Anyone who shares 4ENote, or an app
built from its code, must release their full source code under the same
license.

Versions published before this license change were released under the MIT
License, and those copies remain under MIT.

### Third-party notices

The document scanner finds page corners with **DocQuadNet** from the
[MakeACopy](https://github.com/egdels/makeacopy) project, licensed under the
Apache License 2.0. The model file `app/src/main/assets/docquad/docquadnet256_trained_opset17.ort`
is included unchanged from MakeACopy commit `cdf0e66`.
