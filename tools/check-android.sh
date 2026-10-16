#!/usr/bin/env bash
# Compiles the Android-specific sources against stubs (see tools/android-check/build.gradle.kts).
# Needs: a JDK 17+, and network access to Maven Central + raw.githubusercontent.com (for android.jar, once).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
jar="${ANDROID_JAR:-$here/.cache/android-35.jar}"
if [ ! -s "$jar" ]; then
  mkdir -p "$(dirname "$jar")"
  echo "Downloading android.jar (API 35 stubs, ~26 MB) ..."
  curl -fsSL -o "$jar" https://raw.githubusercontent.com/Sable/android-platforms/master/android-35/android.jar
fi
"$here/../gradlew" -p "$here/android-check" --console=plain -PandroidJar="$jar" compileKotlinDesktop "$@"
