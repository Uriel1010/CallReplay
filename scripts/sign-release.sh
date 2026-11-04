#!/usr/bin/env bash
# Sign an unsigned release APK with the long-term key and preview migration proof.
set -euo pipefail
: "${JAVA_HOME:?Set JAVA_HOME}"
: "${ANDROID_HOME:?Set ANDROID_HOME}"
: "${CALLREPLAY_KEYSTORE:?Path to the private PKCS12 release key}"
: "${CALLREPLAY_PASSWORD_FILE:?Path to a private file containing its password}"
if (( $# != 2 )); then
    echo 'Usage: bash scripts/sign-release.sh unsigned.apk output-release.apk' >&2
    exit 2
fi
root=$(cd "$(dirname "$0")/.." && pwd)
signer="$ANDROID_HOME/build-tools/36.0.0/lib/apksigner.jar"
# Android 11+ supports v3. Rotating at API 28 signs all supported Android
# versions with the new key, rather than retaining the debug key on Android 11/12.
"$JAVA_HOME/bin/java" -jar "$signer" sign \
    --ks "$CALLREPLAY_KEYSTORE" --ks-key-alias callreplay \
    --ks-pass "file:$CALLREPLAY_PASSWORD_FILE" \
    --lineage "$root/signing/callreplay.lineage" --rotation-min-sdk-version 28 \
    --v1-signing-enabled false --v2-signing-enabled false --v3-signing-enabled true \
    --v4-signing-enabled false --out "$2" "$1"
"$JAVA_HOME/bin/java" -jar "$signer" verify --verbose --print-certs --min-sdk-version 30 "$2"
