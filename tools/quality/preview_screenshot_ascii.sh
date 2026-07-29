#!/usr/bin/env bash
set -euo pipefail

source_root="$1"
mode="$2"
temporary_root="$(mktemp -d /tmp/musicapp-screenshot.XXXXXX)"

cleanup() {
  find "$temporary_root" -depth -delete
}
trap cleanup EXIT

rsync -a \
  --exclude='.git/' \
  --exclude='.gradle/' \
  --exclude='build/' \
  "$source_root/" \
  "$temporary_root/"

task_java_home="${JAVA_HOME:-}"
if [[ -z "$task_java_home" && -d "/Applications/Android Studio.app/Contents/jbr/Contents/Home" ]]; then
  task_java_home="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
fi

case "$mode" in
  update)
    env JAVA_HOME="$task_java_home" "$temporary_root/gradlew" \
      -p "$temporary_root" \
      :app:updateDebugScreenshotTest
    mkdir -p "$source_root/app/src/screenshotTestDebug/reference"
    rsync -a \
      "$temporary_root/app/src/screenshotTestDebug/reference/" \
      "$source_root/app/src/screenshotTestDebug/reference/"
    ;;
  validate)
    env JAVA_HOME="$task_java_home" "$temporary_root/gradlew" \
      -p "$temporary_root" \
      :app:validateDebugScreenshotTest
    mkdir -p "$source_root/app/build/reports/screenshotTest/preview/debug"
    rsync -a \
      "$temporary_root/app/build/reports/screenshotTest/preview/debug/" \
      "$source_root/app/build/reports/screenshotTest/preview/debug/"
    ;;
  *)
    echo "Unknown screenshot mode: $mode" >&2
    exit 2
    ;;
esac
