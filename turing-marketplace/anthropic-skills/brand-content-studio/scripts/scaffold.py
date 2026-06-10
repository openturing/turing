#!/usr/bin/env python3
"""Build a beat-structured markdown skeleton for a piece of content.

Mirrors the brand beat model: a Hook beat first, a Call to Action beat last.
Usage:
    python scripts/scaffold.py --format article --title "My title" \
        --beats "Hook,Concept,Brazilian case,Immediate application,CTA"

If --beats is omitted, a sensible default per format is used.
"""
import argparse
import sys

DEFAULT_BEATS = {
    "article": ["Hook", "Problem", "How it works", "Proof", "Call to Action"],
    "video-script": ["Cold open", "Concept", "Demo", "Outro"],
    "newsletter": ["Hook", "What's new", "Why it matters", "Subscribe CTA"],
    "one-pager": ["Headline", "Problem", "Solution", "Proof", "Call to Action"],
}


def scaffold(fmt: str, title: str, beats: list[str]) -> str:
    title = title.strip() or "Untitled"
    if not beats:
        beats = DEFAULT_BEATS.get(fmt, ["Hook", "Development", "Call to Action"])
    out = [f"# {title}", ""]
    for beat in beats:
        out.append(f"## {beat}")
        out.append("")
        out.append("_…_")
        out.append("")
    return "\n".join(out).rstrip() + "\n"


def main() -> int:
    p = argparse.ArgumentParser(description="Build a beat-structured markdown skeleton.")
    p.add_argument("--format", required=True,
                   choices=["article", "video-script", "newsletter", "one-pager"])
    p.add_argument("--title", default="Untitled")
    p.add_argument("--beats", default="",
                   help="Comma-separated beat labels. Blank → per-format default.")
    args = p.parse_args()
    beats = [b.strip() for b in args.beats.split(",") if b.strip()]
    sys.stdout.write(scaffold(args.format, args.title, beats))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
