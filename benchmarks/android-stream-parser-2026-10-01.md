# Android streaming parser: host evidence (2026-10-01)

This compares synchronous parsing, JNI transport, public AST adaptation and stream-buffer accumulation. It does not measure Compose layout/drawing, phone frame time or Android process memory.

## Reproducible workload

- Host: macOS, Android Studio JBR 21, JVM debug unit-test worker.
- Source arrives in 32 UTF-16-unit chunks. Synthetic time advances by 1 ms per chunk; visible prefixes publish every 50 ms, followed by a final publication.
- Both paths disable the global parsed-document cache. The old optional benchmark enables that cache and its warmed timings cannot establish streaming parse cost.
- Two warmup runs per path, then five alternating batch/incremental runs. No timing assertions.
- `chat-standard`: 99,910 UTF-8 bytes, 3,032 chunks, 61 publications; repeated paragraphs, headings, code fences, lists, quotes, inline links and GFM tables with Chinese/emoji text.
- `readme-mixed`: 68,282 UTF-8 bytes, 2,129 chunks, 43 publications; the existing bundled README fixture repeated four times.

| Scenario | Uncached batch median | Incremental median | Relative speed | Cumulative reused blocks |
| --- | ---: | ---: | ---: | ---: |
| chat-standard | 967.150 ms | 47.763 ms | 20.25× | 87,720 |
| readme-mixed | 226.056 ms | 226.234 ms | No measured improvement | 3 |

Reused blocks are summed over all publications, not a count of distinct document blocks. The mixed fixture contains extension syntax that deliberately retains the established batch callback path; it demonstrates the current optimization boundary.

## Behavior and boundaries

The private session sends a complete prefix to the owned Rust parser, receives a replacement tail plus the retained top-level block count, and keeps the retained public AST node instances. Compose receives that parsed document once and uses stable, Bundle-compatible `Long` keys for its stream blocks. The public reader/stream API is unchanged, and ordinary readers preserve their existing parsing/cache behavior.

Custom plugin registries, enabled HTML postprocessing, details, footnotes and any dollar sign keep the hook-aware batch parser. This preserves opaque callbacks that depend on host state and legacy extension syntax/source spans. Potential reference definitions cause the Rust session to replace the full document, so a late definition can resolve an earlier link. Source replacement/shortening resets the native retained prefix. Missing JNI retains the pure JVM fallback. Native sessions are confined to their owner worker and close on that worker when streaming finishes or the composition is disposed; decode/adaptation errors discard the native session before another update.

These measurements were captured before background scheduling was introduced. Eligible public streams now parse and decode on a serial worker; Markup adaptation and publication remain on the UI thread. These host timings establish reduced parser/transport work for eligible documents; they do not establish a frame-time improvement or quantify the scheduling change. Device validation and four-ABI packaging are separate gates.

## Commands

```sh
SMOOTH_MARKDOWN_BENCH=1 JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew :smoothmarkdown:testDebugUnitTest \
  --tests '*ReaderPerformanceBenchmark.incrementalStreamAgainstUncachedBatch' \
  -x buildAndroidRustParser --info

JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew :smoothmarkdown-core:test :smoothmarkdown:testDebugUnitTest \
  :smoothmarkdown:assembleDebug :smoothmarkdown-core:jar -x buildAndroidRustParser

PATH='/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin:'"$PATH" \
  python3 tools/check_public_api.py
```

The exclusion only avoids rebuilding mobile binaries during host measurement; a release must rebuild all mobile ABIs without it.

Final host gate: Core 17 tests (1 opt-in benchmark skipped), Reader 484 tests (3 opt-in benchmarks skipped), zero failures/errors. Eight new session tests include prefix-by-prefix AST/source-coordinate parity, stable block/key identity, late reference invalidation, source reset, same-instance registry mutation, opaque plugin host state, HTML postprocessing and isolated missing-native fallback. Public JVM signatures match the reviewed baseline without updating it.
