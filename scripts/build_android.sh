#!/usr/bin/env bash
# Builds the Android release bundle/APK. Signing comes from env (ANDROID_KEYSTORE_PATH/PASSWORD, ANDROID_KEY_ALIAS/PASSWORD);
# without it the release artifacts are unsigned and must be signed by the owner before upload.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tools/asset_import/pack_runtime_assets.py
./gradlew :app:bundleRelease :app:assembleRelease
ls -la app/build/outputs/bundle/release/ app/build/outputs/apk/release/
