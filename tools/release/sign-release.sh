#!/usr/bin/env bash
# Signs a release APK for publishing: with 4ENote's release key and the key-rotation proof from
# the old development key, so 0.3.0 and older (development-signed) update to it in place on
# Android 9+ instead of having to be uninstalled (which would delete the notes).
#   tools/release/sign-release.sh [in.apk] [out.apk]
# Keys and the proof live outside the project, in ~/.config/4enote (back that folder up).
set -euo pipefail
in="${1:-app/build/outputs/apk/release/app-release.apk}"
out="${2:-app/build/outputs/apk/release/4ENote.apk}"
conf="$HOME/.config/4enote"
props="$conf/signing.properties"
[ -f "$props" ] && [ -f "$conf/lineage.bin" ] || { echo "Missing $props or $conf/lineage.bin" >&2; exit 2; }
pass="$(grep '^storePassword=' "$props" | cut -d= -f2-)"
tools="${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools/37.0.0"
export PATH="${JAVA_HOME:-$HOME/android-studio/jbr}/bin:$PATH"
SIGN_PASS="$pass" "$tools/apksigner" sign \
    --ks "$HOME/.android/debug.keystore" --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android \
    --next-signer --ks "$conf/release.jks" --ks-key-alias 4enote --ks-pass env:SIGN_PASS --key-pass env:SIGN_PASS \
    --lineage "$conf/lineage.bin" --rotation-min-sdk-version 28 \
    --out "$out" "$in" < /dev/null
"$tools/apksigner" verify --print-certs "$out" | grep -E 'Signer.*SHA-256'
