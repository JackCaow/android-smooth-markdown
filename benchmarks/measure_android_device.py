#!/usr/bin/env python3
"""Measure the Debug Demo on a single Android emulator with adb and gfxinfo.

Run after installing app-debug.apk:
  python3 benchmarks/measure_android_device.py --serial emulator-5554 --runs 3
"""

import argparse
import json
import re
import subprocess
import time

PACKAGE = "com.jackcaow.smoothmarkdown.demo"
ACTIVITY = PACKAGE + "/.PerformanceActivity"
EXPECTED_SHA = "898167bf8eea424bd1f697da1d43d6f3a7412d0e846132b390122aaec487524a"


def percentile(values, fraction):
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, round((len(ordered) - 1) * fraction))] if ordered else None


def run_adb(serial, *args):
    return subprocess.check_output(["adb", "-s", serial, *args], text=True, stderr=subprocess.STDOUT)


def gfx_frames(serial):
    report = run_adb(serial, "shell", "dumpsys", "gfxinfo", PACKAGE, "framestats")
    block = report.split("---PROFILEDATA---", 1)[-1].split("---PROFILEDATA---", 1)[0]
    lines = [line.strip() for line in block.splitlines() if line.strip()]
    if len(lines) < 2:
        return []
    names = lines[0].split(",")
    completed = names.index("FrameCompleted")
    return [int(fields[completed]) for line in lines[1:]
            if (fields := line.split(",")) and len(fields) > completed and fields[completed].isdigit()]


def memory_kib(serial):
    report = run_adb(serial, "shell", "dumpsys", "meminfo", PACKAGE)
    match = re.search(r"TOTAL PSS:\s*(\d+).*?TOTAL RSS:\s*(\d+)", report, re.S)
    if not match:
        raise RuntimeError("Could not read app PSS/RSS")
    return {"pssKiB": int(match[1]), "rssKiB": int(match[2])}


def timed_events(serial, mode):
    deadline = time.monotonic() + 70
    while time.monotonic() < deadline:
        report = run_adb(serial, "logcat", "-d", "-s", "SmoothMarkdownPerf:I", "*:S")
        first = re.search(rf"firstContentDraw mode={mode} elapsedMs=([\d.]+) visibleBytes=(\d+)", report)
        done = re.search(rf"complete mode={mode} elapsedMs=([\d.]+).*bytes=(\d+) matches=(\w+) sha256=([0-9a-f]+)", report)
        if first and done:
            if int(done[2]) != 68282 or done[3] != "true" or done[4] != EXPECTED_SHA:
                raise RuntimeError("Final content differs from fixture: " + done[0])
            return float(first[1]), int(first[2]), float(done[1]), report
        time.sleep(0.3)
    raise TimeoutError(f"{mode} did not draw and complete within 70 seconds")


def measure(serial, mode):
    run_adb(serial, "shell", "am", "force-stop", PACKAGE)
    run_adb(serial, "logcat", "-c")
    launch = run_adb(serial, "shell", "am", "start", "-W", "-n", ACTIVITY, "--es", "mode", mode)
    launch_ms = int(re.search(r"TotalTime: (\d+)", launch)[1])
    run_adb(serial, "shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
    first_ms, first_bytes, complete_ms, report = timed_events(serial, mode)
    stream_frames = gfx_frames(serial) if mode == "stream" else []
    before_scroll = memory_kib(serial)
    run_adb(serial, "shell", "uiautomator", "dump", "/sdcard/smooth-perf.xml")
    tree = run_adb(serial, "exec-out", "cat", "/sdcard/smooth-perf.xml")
    if "68282/68282 bytes" not in tree or "complete" not in tree:
        raise RuntimeError("Final status is missing in UI tree")
    time.sleep(0.5)
    scroll_intervals = []
    scroll_frames = 0
    for _ in range(5):
        run_adb(serial, "shell", "dumpsys", "gfxinfo", PACKAGE, "reset")
        run_adb(serial, "shell", "input", "swipe", "540", "1800", "540", "500", "450")
        time.sleep(0.2)
        frames = gfx_frames(serial)
        scroll_frames += len(frames)
        scroll_intervals += [(b - a) / 1_000_000 for a, b in zip(frames, frames[1:]) if b > a]
    after_scroll = memory_kib(serial)
    return {
        "mode": mode,
        "amStartTotalMs": launch_ms,
        "firstContentDrawMs": first_ms,
        "firstVisibleBytes": first_bytes,
        "completionMs": complete_ms,
        "streamFrameCount": len(stream_frames),
        "scrollFrames": scroll_frames,
        "scrollIntervalP50Ms": percentile(scroll_intervals, .5),
        "scrollIntervalP95Ms": percentile(scroll_intervals, .95),
        "scrollIntervalsOver16_7Ms": sum(value > 16.7 for value in scroll_intervals),
        "scrollIntervalCount": len(scroll_intervals),
        "beforeScroll": before_scroll,
        "afterScroll": after_scroll,
        "completedBytes": 68282,
        "completedSha256": EXPECTED_SHA,
        "publishes": int(re.search(r"publishes=(\d+)", report)[1]) if mode == "stream" else 0,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", default="emulator-5554")
    parser.add_argument("--runs", type=int, default=3)
    args = parser.parse_args()
    for mode in ("static", "stream"):
        for index in range(args.runs):
            result = measure(args.serial, mode)
            print(json.dumps({"run": index + 1, **result}), flush=True)


if __name__ == "__main__":
    main()
