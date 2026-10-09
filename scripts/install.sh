#!/usr/bin/env bash
# Build and install on the connected phone, keeping app data (in-place update).
# Usage: scripts/install.sh [debug|release]   (default: release)
set -euo pipefail
cd "$(dirname "$0")/.."

variant="${1:-release}"
case "$variant" in
  debug) task=assembleDebug ;;
  release) task=assembleRelease ;;
  *) echo "Unknown variant: $variant (debug|release)" >&2; exit 1 ;;
esac
apk="app/build/outputs/apk/${variant}/app-${variant}.apk"

if ! grep -q '^aht.storeFile=' "${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties" 2>/dev/null; then
  echo "No personal signing key configured (aht.* in ~/.gradle/gradle.properties)." >&2
  echo "Refusing to install: an APK signed with another key can't update the one on the phone (same key as the habit tracker)." >&2
  exit 1
fi

./gradlew -q "$task"
adb install -r "$apk"
# Release builds: compile ahead of time right away instead of waiting for the phone to do it overnight.
if [ "$variant" = release ]; then adb shell cmd package compile -m speed -f dev.eduarddragu.anotherreminderapp >/dev/null; fi
adb shell monkey -p dev.eduarddragu.anotherreminderapp -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
echo "Installed ${variant} build and launched it."
