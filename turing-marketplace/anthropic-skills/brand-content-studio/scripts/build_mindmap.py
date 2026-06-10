#!/usr/bin/env python3
"""Turn a body of text (e.g. concatenated abstracts) into a Mermaid mind-map.

Root = the central term; branches = the top-N most frequent meaningful words
(stop-words and the term itself removed), title-cased.

Usage:
    python scripts/build_mindmap.py --term "payments" --file abstracts.txt
    python scripts/build_mindmap.py --term "payments" --top 10 --file abstracts.txt
"""
import argparse
import re
import sys
from collections import Counter

STOP_WORDS = {
    # pt
    "a", "o", "e", "de", "da", "do", "das", "dos", "um", "uma", "que", "para",
    "com", "por", "no", "na", "nos", "nas", "se", "as", "os", "ao", "aos", "em",
    "ou", "como", "mais", "mas", "foi", "ser", "sua", "seu", "sao", "este",
    "esta", "isso",
    # en
    "the", "of", "and", "to", "in", "is", "that", "for", "with", "on", "are",
    "be", "by", "an", "it", "or", "from", "this", "was", "were", "which",
    "their", "its",
}
TOKEN = re.compile(r"[^\W\d_]+", re.UNICODE)


def build_mindmap(text: str, term: str, top_n: int = 8) -> str:
    root = (term or "").strip() or "Topic"
    term_lower = (term or "").strip().lower()
    counts: Counter[str] = Counter()
    for token in TOKEN.findall((text or "").lower()):
        if len(token) < 3 or token in STOP_WORDS or token == term_lower:
            continue
        counts[token] += 1
    branches = [w.capitalize() for w, _ in counts.most_common(top_n)]
    lines = ["mindmap", f"  root(({root.capitalize()}))"]
    lines += [f"    {b}" for b in branches]
    return "\n".join(lines) + "\n"


def main() -> int:
    p = argparse.ArgumentParser(description="Build a Mermaid mind-map from text.")
    p.add_argument("--term", required=True)
    p.add_argument("--top", type=int, default=8)
    p.add_argument("--file", help="Source file; if omitted, reads stdin.")
    args = p.parse_args()
    text = open(args.file, encoding="utf-8").read() if args.file else sys.stdin.read()
    sys.stdout.write(build_mindmap(text, args.term, args.top))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
