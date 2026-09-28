#!/bin/sh
# GitHub Actions uses gradle/actions/setup-gradle directly. This lightweight script is kept for project completeness.
if command -v gradle >/dev/null 2>&1; then exec gradle "$@"; fi
echo "Gradle is not installed locally. Use GitHub Actions (Actions -> Build SubtitlePad APK -> Run workflow), or install Gradle." >&2
exit 1
