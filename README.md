# 4ENote

A native Android handwriting note-taking app for tablets, built for personal
study use. Open source (GPL-3.0).

## Download

[**⬇ Download the latest APK**](https://github.com/GerkoOo/4ENote/releases/latest/download/4ENote.apk)
· [all releases](https://github.com/GerkoOo/4ENote/releases)

Open the file on your Android tablet (Android 8 or newer) and allow your browser
to install apps when asked. This is a beta: Google Drive and OneDrive sync are
not ready yet.

## Features

- Pen, ink pen, calligraphy and highlighter; eraser, lasso and layers
- Hold a drawn line, circle, triangle or rectangle to straighten it; tables
- Handwriting to text, and handwritten maths to LaTeX (offline, open-source model)
- Import PDFs and images (with dark-mode colour inversion), scan documents,
  export and share as PDF
- Lined, grid, dotted or blank paper; dark mode; full-screen page view
- Quick pen dial, pen side buttons, scribble to erase, finger gestures

## Stack

- Kotlin, Jetpack Compose (Material 3), Room
- PdfBox-Android for PDF export, ONNX Runtime + Pix2Text for maths recognition,
  JLaTeXMath for typesetting, Google ML Kit for handwriting and scanning

## Building

```
export JAVA_HOME=/path/to/jdk-17-or-newer   # must be a full JDK, not a JRE
./gradlew assembleDebug
```

Requires the Android SDK with platform 37 and build-tools 37.0.0 installed,
and `local.properties` pointing `sdk.dir` at your SDK (not committed).

## License

4ENote is licensed under the [GNU General Public License v3.0](LICENSE).
You may use, study, change and share it. Anyone who shares 4ENote, or an app
built from its code, must release their full source code under the same
license.

Versions published before this license change were released under the MIT
License, and those copies remain under MIT.
