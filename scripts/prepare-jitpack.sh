#!/usr/bin/env bash
# Source build tools only. Applications receive the JNI binaries inside the AAR.
set -euo pipefail

if [[ "$(uname -s)" != Linux ]]; then
    echo 'The JitPack bootstrap requires its Linux build environment.' >&2
    exit 1
fi
python3 --version
python3 -c 'import sys; sys.exit("Python 3.5 or newer is required") if sys.version_info < (3, 5) else None'
if [[ -n "${JAVA_HOME:-}" ]]; then
    "$JAVA_HOME/bin/java" -version
else
    java -version
fi

task_sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export ANDROID_HOME="$task_sdk_root"
export ANDROID_SDK_ROOT="$task_sdk_root"
export ANDROID_NDK_HOME="$task_sdk_root/ndk/28.2.13676358"
export ANDROID_NDK_ROOT="$ANDROID_NDK_HOME"

# Google's repository2-3.xml, remotePackage cmdline-tools;12.0, Linux archive:
# https://dl.google.com/android/repository/repository2-3.xml
# complete/checksum type=sha1 d313adb7aedccf6cf0cfca51ec180f0059f5f8f8
# This version supports Java 17 and avoids the image's legacy JAXB-based tools.
task_tools_url='https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip'
task_tools_sha1='d313adb7aedccf6cf0cfca51ec180f0059f5f8f8'
task_tools_tmp="$(mktemp -d)"
trap 'rm -rf "$task_tools_tmp"' EXIT
curl --proto '=https' --tlsv1.2 --fail --show-error --location \
    "$task_tools_url" --output "$task_tools_tmp/tools.zip"
printf '%s  %s\n' "$task_tools_sha1" "$task_tools_tmp/tools.zip" | sha1sum --check
unzip -q "$task_tools_tmp/tools.zip" -d "$task_tools_tmp/unpacked"
mkdir -p "$task_sdk_root/cmdline-tools/12.0"
cp -R "$task_tools_tmp/unpacked/cmdline-tools/." "$task_sdk_root/cmdline-tools/12.0/"
task_sdkmanager="$task_sdk_root/cmdline-tools/12.0/bin/sdkmanager"
"$task_sdkmanager" --sdk_root="$task_sdk_root" --version
# Process substitution ignores only yes's expected SIGPIPE. sdkmanager failure
# still exits the script; a pipe with pipefail would reject successful acceptance.
"$task_sdkmanager" --sdk_root="$task_sdk_root" --licenses < <(yes)
"$task_sdkmanager" --sdk_root="$task_sdk_root" \
    'platforms;android-35' 'build-tools;35.0.0' 'ndk;28.2.13676358'
test -f "$ANDROID_NDK_HOME/source.properties"
grep '^Pkg.Revision' "$ANDROID_NDK_HOME/source.properties"

task_cargo_bin="${CARGO_HOME:-$HOME/.cargo}/bin"
if [[ ! -x "$task_cargo_bin/rustup" ]]; then
    curl --proto '=https' --tlsv1.2 --fail --show-error --location \
        'https://sh.rustup.rs' --output "$task_tools_tmp/rustup-init.sh"
    sh "$task_tools_tmp/rustup-init.sh" -y --profile minimal --default-toolchain stable --no-modify-path
fi
export PATH="$task_cargo_bin:$PATH"
rustup toolchain install stable --profile minimal
rustup default stable
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
rustc --version
cargo --version
