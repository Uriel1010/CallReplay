# Updates and production signing

CallReplay distributes signed production APKs through
[GitHub Releases](https://github.com/Uriel1010/CallReplay/releases).
Use **Settings → About → Check for updates**, or add
`https://github.com/Uriel1010/CallReplay` as a GitHub source in Obtainium.
Use the APK ending in `-release.apk`. Stable releases are the default; previews
require opting in. Obtainium and the in-app updater use the same APK and signing key.

Automatic checking is off initially. When enabled, CallReplay contacts GitHub on
app foreground at most once per day. Manual checking also contacts GitHub.
GitHub receives normal connection information, including your IP address and the
CallReplay version in the User-Agent. Caller details and recordings are never
included. There is no update account or embedded GitHub token.

Downloads require an explicit tap and show progress, cancellation and retry.
CallReplay checks the expected size and SHA-256, package ID, increasing version
code, Android compatibility, non-debuggable status and compatible signing identity.
Android verifies the APK signature again and asks for installation confirmation.
If necessary, allow installation from CallReplay and then return and tap Install
again. Installation is deferred during a call or recording. Cancelling an update
keeps the existing app and recordings.

## Maintainer release procedure

Keep the production PKCS12 key and its password backed up separately and privately.
Neither belongs in Git, public build artifacts, issue attachments or release assets.
The public `signing/callreplay.lineage` is a proof of rotation from the original
CallReplay debug-preview certificate to the production certificate. It contains
certificates and signatures, not private keys. Do not generate a new production
key for each release. Do not uninstall the app to bypass a signing mismatch.

Production certificate SHA-256:
`33ec7da8dec0b3ba3797cad79ea1d9984a96e5d4f25fe4d8c604bbf105d4b6b4`.

1. Increase the hardcoded `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Run the bounded build script with `:app:assembleRelease`, lint and relevant tests.
3. Set `JAVA_HOME`, `ANDROID_HOME`, `CALLREPLAY_KEYSTORE` and
   `CALLREPLAY_PASSWORD_FILE` to the local toolchain and private signing files.
4. Run `bash scripts/sign-release.sh app/build/outputs/apk/release/app-release-unsigned.apk /path/to/CallReplay-VERSION-release.apk`.
5. Run `python3 scripts/release-metadata.py /path/to/CallReplay-VERSION-release.apk`.
6. Verify in-place upgrade, settings and recording-folder access on a device.
7. Publish a matching source tag and release with the signed APK, `SHA256SUMS`,
   `callreplay-update.json` and accurate release notes. Mark previews explicitly.

The release manifest has schema version 1, the package ID, numeric version code,
version name, minimum Android SDK, APK asset name, exact byte size and SHA-256.
The updater accepts assets only from this repository's GitHub releases. Future
store-specific builds should coordinate their updater policy with that store.
