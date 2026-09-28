#!/usr/bin/env python3
"""Copy the Chat List Demo's authored Markdown strings from the Flutter example."""
from pathlib import Path
import argparse
import re

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT.parent / "flutter-smooth-markdown/example/lib/chat_list_demo.dart"
DEST = ROOT / "app/src/main/assets/examples/chat-list"
METHODS = {
    "code": "_getCodeExampleResponse",
    "features": "_getMarkdownFeaturesResponse",
    "performance": "_getPerformanceResponse",
    "table": "_getTableResponse",
}


def extracted() -> dict[str, str]:
    source = SOURCE.read_text()
    welcome = re.search(r"_loadWelcomeMessage\(\) \{.*?content: '''(.*?)'''", source, re.S)
    if not welcome:
        raise ValueError("Flutter welcome message not found")
    texts = {"welcome": welcome.group(1)}
    for name, method in METHODS.items():
        found = re.search(rf"String {method}\(\) \{{\s*return '''(.*?)''';", source, re.S)
        if not found:
            raise ValueError(f"Flutter response {method} not found")
        # Dart's backslash suppresses interpolation; the rendered Markdown contains '$'.
        texts[name] = found.group(1).replace(r"\$", "$")
    return texts


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    for name, value in extracted().items():
        target = DEST / f"{name}.md"
        if args.check:
            if target.read_text() != value:
                raise SystemExit(f"Out of date: {target}")
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(value)
    print("Flutter Chat List Markdown matches: welcome + four responses")


if __name__ == "__main__":
    main()
