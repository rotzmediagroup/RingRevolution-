#!/usr/bin/env bash
# iOS is the second platform target (ADR-001). This script documents the path; it needs macOS + Xcode:
#  1. Convert :core to Kotlin Multiplatform (add iosArm64/iosSimulatorArm64 targets, commonMain sources).
#  2. ./gradlew :core:linkReleaseFrameworkIosArm64 → DumplingRingsCore.framework
#  3. Open ios/DumplingRings.xcodeproj (SwiftUI shell, Metal/Canvas board renderer port of app/ui/board/BoardCanvas.kt)
#  4. Share content/ and app/src/main/assets/ as bundle resources.
echo "iOS build not available in this environment (requires Xcode). See docs/RELEASE_CHECKLIST.md." >&2
exit 2
