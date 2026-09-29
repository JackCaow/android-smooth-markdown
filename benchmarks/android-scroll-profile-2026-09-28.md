# Long document scroll profile — 2026-09-28

The 68,282-byte [device benchmark](android-device.md) was repeated on `emulator-5554` from Android commit `c6983bb` (Android 15 / API 35, `sdk_gphone64_arm64`, Debug APK, 1080×2400, 420 dpi, 2 GiB guest RAM). Both the unchanged renderer and the experimental renderer used the same `benchmarks/measure_android_device.py` with three static and three rapid-stream runs each:

```sh
./gradlew :app:assembleDebug --offline
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
python3 benchmarks/measure_android_device.py --serial emulator-5554 --runs 3
```

The experiment wrapped each `LazyColumn` item in `Box(Modifier.graphicsLayer())`, attempting to reuse each item's drawn text. It is **not present in the final source** because the device measurements worsened.

| Mode | Renderer | First content draw, median | Five-swipe frame-completion interval p50, median | p95, median | PSS after swipes, median |
| --- | --- | ---: | ---: | ---: | ---: |
| Static | Original | 1.56 s | 36.8 ms | 52.2 ms | 98.3 MiB |
| Static | Item layer | 2.15 s | 48.9 ms | 101.4 ms | 98.0 MiB |
| Stream | Original | 2.32 s | 39.2 ms | 53.6 ms | 100.7 MiB |
| Stream | Item layer | 2.42 s | 49.0 ms | 120.7 ms | 101.8 MiB |

All 12 runs matched the final 68,282-byte SHA-256 and visible completion status; all stream runs made 42 intermediate publishes. The complete per-run output is in [original JSONL](android-scroll-baseline-2026-09-28.jsonl) and [layer experiment JSONL](android-scroll-layer-experiment-2026-09-28.jsonl). The p50 and p95 values are medians of each run's five-swipe statistic, not pooled percentiles.

To attribute the work during static-document scrolling, the already loaded original APK was sampled during seven upward swipes:

```sh
adb -s emulator-5554 shell am start -W -n com.jackcaow.smoothmarkdown.demo/.PerformanceActivity --es mode static
adb -s emulator-5554 shell simpleperf record --app com.jackcaow.smoothmarkdown.demo -e cpu-clock -f 99 -g --duration 8 -o /data/local/tmp/smooth-scroll.perf.data
adb -s emulator-5554 shell simpleperf report -i /data/local/tmp/smooth-scroll.perf.data --sort comm,dso --percent-limit 0.5
```

`cpu-clock` was used because the emulator does not support the default `cpu-cycles` event. Across 265 samples, 69.06% of sample overhead was in `RenderThread` kernel paths, 7.92% in app ART libraries and 5.66% in app JIT code. The call graph placed 64.53% accumulated overhead under `QemuPipeStream::commitBufferAndReadFully`, 54.72% under Skia's `AtlasTextOp::onPrepareDraws`, and 71.70% under `RenderThread::DrawFrameTask::run`. See the [overhead table](android-scroll-simpleperf-summary-2026-09-28.txt) and [call graph extract](android-scroll-simpleperf-callgraph-2026-09-28.txt). Kernel symbols are restricted in this emulator, and the sampling run is too small to rank individual Compose functions reliably.

The profile points to emulator graphics/text submission as the dominant observed cost for these swipes. The item-layer attempt increased both frame intervals and first draw time, so no renderer change was retained. These Debug emulator numbers do not establish physical-device or release-build behavior, and another device or rendering backend may have a different bottleneck. A release-build trace on a representative physical device is the next evidence needed before a renderer optimization is chosen.
