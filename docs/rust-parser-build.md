# Building the owned Rust parser

AARs built from this source revision contain `libsmooth_markdown_rust_jni.so` for
`arm64-v8a`, `armeabi-v7a`, `x86`, and `x86_64`. Applications install the
normal Maven dependency; Cargo, Rust, and the NDK are **source build tools**,
not requirements for library consumers. The parser and JNI bridge contain no
third-party crates or JNI bridge libraries.

This change prepares the next 0.3.0 package; it does not publish that version.

The `smoothmarkdown-core` JAR remains usable on a JVM without native binaries.
It uses the Kotlin implementation when the native library is unavailable.
Android's packaged reader uses Rust for supported built-in grammar; host parser
callbacks retain the Kotlin parsing path so existing plugin behavior is preserved.

## Source build prerequisites

- JDK 17 or newer, Python 3, Android SDK API 37, and Android NDK 28 or newer.
- Stable Rust and the four Android targets:

```sh
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
sdkmanager 'platforms;android-37.0' 'ndk;28.2.13676358'
```

Install Rust through its official instructions at <https://www.rust-lang.org/tools/install>.
The checked-in `rust-core/` tree is the owned parser source; no external repository
or Cargo dependency download is needed to build it. Cargo runs offline with the
checked-in lockfile. `rust-core/SOURCE.sha256` records the exact owned source
mirror; verify it with `cd rust-core && shasum -a 256 -c SOURCE.sha256`.
Set `ANDROID_HOME` (or `sdk.dir` in `local.properties`).
`ANDROID_NDK_HOME`/`ANDROID_NDK_ROOT` can select a specific NDK 28+ installation.

```sh
./gradlew :smoothmarkdown-core:test :smoothmarkdown:testDebugUnitTest :smoothmarkdown:assembleRelease
```

`preBuild` builds all four native libraries. Unit tests build desktop JNI on
macOS/Linux and set `smoothmarkdown.rust.required=true`, so a missing native
backend fails the gate instead of silently testing only the fallback. Standalone
JVM consumers of the published Core JAR remain independent of that test setting.

To build binaries directly:

```sh
python3 scripts/build-rust-parser.py --android
python3 scripts/build-rust-parser.py --host --java-home "$JAVA_HOME"
python3 scripts/build-rust-parser.py --check-aar smoothmarkdown/build/outputs/aar/smoothmarkdown-release.aar
```

Outputs are under `smoothmarkdown/build/generated/rust/jniLibs/<ABI>/` and
`.../host/`. Native build tasks track the Rust source, lockfile, header, JNI shim,
and build script as inputs; generated libraries are task outputs. Neither native
binaries nor Cargo build caches are committed.

## 16KB pages and packaging

The native link uses a 16384-byte maximum/common page size and verifies every
ELF `LOAD` segment's alignment. CI uses NDK 28+. Final app ZIP alignment is also
the application's responsibility: use AGP 8.5.1+ and verify its APK/AAB and
runtime on a 16KB-page Android device. An aligned SDK `.so` alone does not prove
an application's APK ZIP alignment.

See Android's official [16KB-page guide](https://developer.android.com/guide/practices/page-sizes).
