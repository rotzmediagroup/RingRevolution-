#!/usr/bin/env bash
# Runs every automated check: core unit/property tests, level content validation, app unit tests, debug build.
set -euo pipefail
cd "$(dirname "$0")/.."
echo "== core tests"; ./gradlew -q :core:test
echo "== level validation"; ./gradlew -q :tools:levels:installDist; tools/levels/build/install/levels/bin/levels validate
echo "== level report"; tools/levels/build/install/levels/bin/levels report
echo "== runtime asset manifest check"; python3 tools/asset_import/check_runtime_assets.py
echo "== app unit tests + lint"; ./gradlew -q :app:testDebugUnitTest :app:lintDebug
echo "== debug build"; ./gradlew -q :app:assembleDebug
echo "ALL CHECKS PASSED"
