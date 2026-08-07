#!/usr/bin/env python3
"""Rewrite the README's screenshot gallery from whatever is in screenshots/.

Run by .github/workflows/screenshots.yml after the emulator run has staged its JPGs. Safe to run
locally too — it only touches the block between the two marker comments in README.md.

The gallery is generated rather than hand-maintained because the capture set is not fixed: the
ScreenshotTest skips any screen it can't reach (a peer that went offline, a room with no traffic),
so a run may legitimately produce ten images instead of eleven. Hand-written <img> tags would rot
into broken links the first time that happened.
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
README = ROOT / "README.md"
SHOTS_DIR = ROOT / "screenshots"

START = "<!-- screenshots:start -->"
END = "<!-- screenshots:end -->"

COLUMNS = 3

# Filename stem (minus the ordering prefix) → the caption shown under the image. Anything not
# listed falls back to a title-cased version of the stem, so adding a capture to the test doesn't
# require editing this file first.
CAPTIONS = {
    "connect": "Connect",
    "search-list": "Searches",
    "search-results": "Search results",
    "downloads": "Downloads",
    "uploads": "Uploads",
    "rooms": "Rooms",
    "room-chat": "Room chat",
    "chat": "Chat",
    "user-profile": "User profile",
    "browse": "Browse",
    "settings": "Settings",
}


def caption(path: pathlib.Path) -> str:
    stem = re.sub(r"^\d+-", "", path.stem)
    return CAPTIONS.get(stem, stem.replace("-", " ").capitalize())


def build_gallery(images: list[pathlib.Path]) -> str:
    """A plain HTML table: GitHub renders it, and unlike a markdown table it can size the images."""
    rows: list[str] = []
    for i in range(0, len(images), COLUMNS):
        chunk = images[i : i + COLUMNS]
        cells = "".join(
            f'<td align="center" width="33%">'
            f'<img src="screenshots/{img.name}" alt="{caption(img)}" width="240"><br>'
            f"<sub>{caption(img)}</sub>"
            f"</td>"
            for img in chunk
        )
        rows.append(f"  <tr>{cells}</tr>")
    return "<table>\n" + "\n".join(rows) + "\n</table>"


def main() -> int:
    images = sorted(SHOTS_DIR.glob("*.jpg"))
    if not images:
        print(f"No images in {SHOTS_DIR} — nothing to do.", file=sys.stderr)
        return 1

    text = README.read_text()
    if START not in text or END not in text:
        print(
            f"README.md is missing the {START} / {END} markers — add them where the gallery "
            "should go.",
            file=sys.stderr,
        )
        return 1

    gallery = build_gallery(images)
    pattern = re.compile(
        re.escape(START) + r".*?" + re.escape(END),
        re.DOTALL,
    )
    README.write_text(pattern.sub(f"{START}\n\n{gallery}\n\n{END}", text))
    print(f"Updated README gallery with {len(images)} screenshot(s).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
