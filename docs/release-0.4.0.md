# Smooth Markdown for Android 0.4.0

## Install

```kotlin
// settings.gradle.kts repositories: google(), mavenCentral(), maven("https://jitpack.io")
implementation("com.github.JackCaow.android-smooth-markdown:smoothmarkdown:0.4.0")
// Parser/HTML only (without Android or Compose):
// implementation("com.github.JackCaow.android-smooth-markdown:smoothmarkdown-core:0.4.0")
```

Reader consumers: Android API 24+, compileSdk 35+, AGP 8.6.0+, Java 17 and Compose Foundation 1.6.8+. The library no longer exports a Compose BOM. Kotlin 1.9.22 and 2.1.20 consumers are verified; match your Compose compiler to Kotlin.

## Changes

- Lower the source baseline to Kotlin 1.9.24, Compose 1.6.8 / Material3 1.2.1, AGP 8.9.1 and Gradle 8.11.1.
- Own the reader selection layer instead of requiring Compose 1.12. Retain native Copy, custom actions, selection handles, drag scrolling, RTL/table ordering, non-text semantic copy and whole-document selection.
- Provide String and AnnotatedString `SmoothSelectableText` overloads for custom builders participating in controller-driven cross-block selection. Opaque custom builders rendering arbitrary Compose Text keep local native selection; migrate cross-block selection to the helper or `context.renderChild` / `renderInlineChildren`.
- Include background stream parsing, latest-prefix coalescing, incremental AST transport and final-layout completion fixes merged since 0.3.1.
- Retain independent JVM Core and four packaged JNI ABIs with 16 KB ELF alignment.

## Validation

- Host tests: 534 passed, 4 skipped, 0 failures.
- API 35 arm64 simulator: 56/56 library UI and 2/2 Demo UI tests passed, including a real native Copy menu tap.
- Local staged Maven consumers: Kotlin 1.9.22 and 2.1.20 clean builds passed with SDK 35 / AGP 8.6.1.
- Public JVM signatures, AAR SDK/AGP metadata, no exported BOM, four JNI ABIs / 16 KB ELF and eight Flutter fixture groups verified.

Public packages are supplied through JitPack. Maven Central has not been published. Existing 0.3.1 artifacts/tags remain unchanged and still require SDK 37.
