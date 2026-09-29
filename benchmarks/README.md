# Reader parser and stream benchmark

For the Compose Demo's repeatable 68,282-byte emulator render and scroll run, see [android-device.md](android-device.md).
For a same-device scroll profile and an experiment that was reverted after regression, see [android-scroll-profile-2026-09-28.md](android-scroll-profile-2026-09-28.md).

This is an opt-in host microbenchmark. It measures parsing and stream accumulation; it does not measure Compose layout, drawing, gesture latency, network images, or device memory.

Fixture: `smoothmarkdown/src/test/resources/performance/flutter-readme.md` is an exact copy of Flutter Smooth Markdown `README.md` at `80e6bb6`, SHA-256 `c84c04eb11485a1102fde7ab104ef2b86636dda4d45d7862d3289148330ef199`. The test repeats it four times with blank-line separators: 68,282 UTF-8 bytes. The rapid stream divides the document into 32-character chunks and advances synthetic time by 1 ms per chunk. A 50 ms update interval yields 42 intermediate publishes; each publish reparses the visible prefix, then completion reparses the full text. This reflects the stream update pattern in Flutter's stream tests. The test runs 3 parser warmups and 10 measured parses, then 2 stream warmups and 5 measured runs. Buffer-only runs isolate accumulation cost.

Run from this repository root:

```sh
SMOOTH_MARKDOWN_BENCH=1 ./gradlew :smoothmarkdown:testDebugUnitTest --tests '*ReaderPerformanceBenchmark' --rerun-tasks --info --offline
```

Look for the `BENCH android` line in Gradle output. On Apple M4, macOS 15.6.1, OpenJDK 21.0.8, Debug JVM test worker, 2026-09-28: full parse median 9.08 ms, maximum 12.19 ms; stream parse-and-buffer median 62.79 ms; buffer-only median 0.48 ms. JVM used heap was 8.3 MiB after warmup GC, reached 20.8 MiB during parses, and returned to 7.3 MiB after GC. These are host worker heap readings, not Android process RSS. First runs were substantially slower due to compilation, JIT and concurrent load; compare warmed repeated runs on the same host.

The benchmark is skipped during normal test runs. No timing threshold is asserted. A 50 ms synthetic interval is a logical schedule, not a real-time producer; the results cannot establish frame smoothness or long-session memory behavior. A device run with rendering and a long streaming session remains necessary for release-level performance acceptance.
