# Android long document device run — 2026-09-28

The Demo now has a **Long document benchmark** button and a directly launchable `PerformanceActivity`. It uses the exact 17,069-byte Flutter README fixture from the host benchmark (`c84c04eb11485a1102fde7ab104ef2b86636dda4d45d7862d3289148330ef199`) four times, separated by blank lines. The rendered document is 68,282 UTF-8 bytes, SHA-256 `898167bf8eea424bd1f697da1d43d6f3a7412d0e846132b390122aaec487524a`.

## Reproduce

```sh
./gradlew :app:assembleDebug --offline
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
python3 benchmarks/measure_android_device.py --serial emulator-5554 --runs 3
```

The script force-stops the Demo for each cold launch, waits for its first Markdown draw and final source hash, checks the visible completion status with `uiautomator`, and records PSS/RSS from `dumpsys meminfo`. It then performs five identical 450 ms upward swipes (`540,1800` to `540,500`), resetting and reading `dumpsys gfxinfo ... framestats` for each swipe. Frame intervals are differences between adjacent `FrameCompleted` timestamps **within** each swipe. Raw per-run data: [android-emulator-2026-09-28.jsonl](android-emulator-2026-09-28.jsonl).

Static mode gives the full document to `SmoothMarkdown` immediately. Stream mode sends 32-character chunks with a 1 ms coroutine delay and synthetic 1 ms logical timestamps to `StreamMarkdownBuffer(intervalMillis = 50)`; exactly 42 intermediate publishes are made before `finish()`. The synthetic timestamps make the publish count deterministic; actual wall-clock completion depends on the emulator.

## Environment and results

`emulator-5554`, `sdk_gphone64_arm64`, Android 15 / API 35, 1080×2400 at 420 dpi, 2 GiB guest RAM, 16.67 ms reported frame interval. Debug APK, three cold launches per mode, same installed build. Local host Gradle cache was reused. Timing is from the activity's `onCreate` to the first nonempty Markdown draw; Android `am start -W` is recorded separately in the raw file.

| Mode | First Markdown draw, median (range) | Full content, median (range) | Scroll frame interval p50, median (range) | Scroll frame interval p95, median (range) | PSS before / after five swipes |
| --- | ---: | ---: | ---: | ---: | ---: |
| Static | 1.83 s (1.59–2.44) | 1.89 s (1.67–2.51) | 39.4 ms (34.7–51.8) | 118.6 ms (55.5–275.1) | 92–93 / 100–102 MiB |
| Stream | 3.13 s (2.38–3.26) | 11.01 s (10.86–13.60) | 48.3 ms (43.4–53.0) | 85.0 ms (80.8–106.8) | 93–103 / 103–105 MiB |

All six runs displayed `68282/68282 bytes ... complete`, logged `matches=true`, and matched the document SHA-256. The stream runs each logged 42 publishes. Scrolling missed the 16.67 ms frame interval on most sampled intervals, so this emulator does not demonstrate smooth long-document scrolling. A bounded prose grouping experiment (8 blocks per LazyColumn item, then 1) made device scroll measurements worse and was reverted; the library renderer is unchanged by this task.

These are Debug emulator observations, not physical-device or release-build acceptance. Frame samples include Compose work, emulator graphics and image loading; they do not identify a single bottleneck. The completion check proves the entire source reached the reader and was reflected in the Demo status, but does not visually inspect every rendered block. A device profile and release-build run are needed before setting a performance budget or choosing a renderer optimization.
