#!/usr/bin/env python3
"""Verify bundled Android Demo fixtures against the sibling Flutter example."""

from pathlib import Path
import subprocess
import sys


SCRIPTS = (
    "sync_flutter_examples.py",
    "sync_flutter_demo_pages.py",
    "sync_flutter_mermaid_examples.py",
    "sync_flutter_streaming_demo.py",
    "sync_flutter_ai_chat.py",
    "sync_flutter_chat_list.py",
    "sync_flutter_conversations.py",
    "sync_flutter_l10n.py",
)


def main() -> None:
    tools = Path(__file__).resolve().parent
    source = tools.parent.parent / "flutter-smooth-markdown/example/lib"
    if not source.is_dir():
        raise SystemExit(f"Flutter example directory does not exist: {source}")
    for script in SCRIPTS:
        print(f"Checking {script}...", flush=True)
        subprocess.run([sys.executable, str(tools / script), "--check"], check=True)
    print("All eight Android Demo fixture groups match the Flutter example.")


if __name__ == "__main__":
    main()
