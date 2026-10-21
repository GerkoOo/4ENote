# 4ENote

A native Android handwriting note-taking app with Google Drive sync, built for
personal study use. Open source (GPL-3.0).

## Stack

- Kotlin, Jetpack Compose (Material 3)
- AGP 9.4.1 with built-in Kotlin compilation
- `androidx.ink` for low-latency stylus/finger handwriting
- Room for local storage
- Google Sign-In (Credential Manager) + Drive REST API for cloud sync

## Status

Early scaffold. Handwriting canvas and Drive sync are being built as separate
modules on top of this shared foundation.

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
