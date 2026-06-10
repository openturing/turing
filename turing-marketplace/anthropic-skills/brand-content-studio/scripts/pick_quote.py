#!/usr/bin/env python3
"""Pick the most representative pull quote for a term from a body of text.

Heuristic: the longest sentence of at least --min-length characters that
mentions the term (case-insensitive); falls back to the longest sentence
overall. Never fabricates — only returns text from the source.

Usage:
    python scripts/pick_quote.py --term "agendado" --min-length 40 --file source.txt
    echo "..." | python scripts/pick_quote.py --term "agendado"
"""
import argparse
import re
import sys

SENTENCE_SPLIT = re.compile(r"(?<=[.!?])\s+")


def pick_quote(text: str, term: str, min_length: int = 40) -> str:
    if not text or not text.strip():
        return ""
    needle = (term or "").strip().lower()
    best_with_term = None
    longest_overall = None
    for raw in SENTENCE_SPLIT.split(text):
        sentence = raw.strip()
        if not sentence:
            continue
        if longest_overall is None or len(sentence) > len(longest_overall):
            longest_overall = sentence
        has_term = bool(needle) and needle in sentence.lower()
        if has_term and len(sentence) >= min_length and (
            best_with_term is None or len(sentence) > len(best_with_term)
        ):
            best_with_term = sentence
    return best_with_term or longest_overall or ""


def main() -> int:
    p = argparse.ArgumentParser(description="Extract the best pull quote for a term.")
    p.add_argument("--term", required=True)
    p.add_argument("--min-length", type=int, default=40)
    p.add_argument("--file", help="Source file; if omitted, reads stdin.")
    args = p.parse_args()
    text = open(args.file, encoding="utf-8").read() if args.file else sys.stdin.read()
    quote = pick_quote(text, args.term, args.min_length)
    if quote:
        sys.stdout.write(quote + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
