#!/usr/bin/env bash
# Build on a shared Linux host with a hard limit for the entire process tree.
set -euo pipefail

: "${JAVA_HOME:?Set JAVA_HOME to Java 17}"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
: "${CALLREPLAY_GRADLE:?Set CALLREPLAY_GRADLE to the Gradle executable}"

cd "$(dirname "$0")/.."
available_kib=$(awk '/MemAvailable:/ {print $2}' /proc/meminfo)
if (( available_kib < 4194304 )); then
    echo "Build refused: less than 4 GiB of host memory is available." >&2
    exit 1
fi

export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}"
export DBUS_SESSION_BUS_ADDRESS="${DBUS_SESSION_BUS_ADDRESS:-unix:path=$XDG_RUNTIME_DIR/bus}"
if (( $# == 0 )); then
    set -- :app:assembleDebug :app:lintDebug
fi

# Do not fall back to an unconstrained build if systemd or cgroup setup fails.
exec systemd-run --user --scope --unit="callreplay-build-$$" \
    -p MemoryHigh=2560M -p MemoryMax=2560M -p MemorySwapMax=0 \
    -p CPUQuota=200% -p TasksMax=256 \
    "$CALLREPLAY_GRADLE" "$@" --no-daemon --max-workers=1 --console=plain \
    -Dorg.gradle.parallel=false -Dorg.gradle.vfs.watch=false \
    '-Dorg.gradle.jvmargs=-Xmx896m -XX:MaxMetaspaceSize=512m -XX:MaxDirectMemorySize=128m -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8' \
    -Pkotlin.compiler.execution.strategy=in-process
