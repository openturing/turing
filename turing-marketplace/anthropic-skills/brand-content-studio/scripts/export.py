#!/usr/bin/env python3
"""Render a markdown draft to a deliverable file (md / html / pdf).

Usage:
    python scripts/export.py --in draft.md --format html
    python scripts/export.py --in draft.md --format pdf --out report.pdf

- md:   copies the source (normalised line endings).
- html: minimal, dependency-free markdown→HTML (headings, paragraphs, lists,
        bold/italic, code) wrapped in a styled page.
- pdf:  tries reportlab; if unavailable, prints guidance and exits non-zero
        rather than failing silently.
"""
import argparse
import html
import os
import re
import sys


def md_to_html(md: str) -> str:
    out = []
    in_list = False
    for line in md.splitlines():
        if re.match(r"^\s*[-*]\s+", line):
            if not in_list:
                out.append("<ul>")
                in_list = True
            out.append("<li>" + inline(line.strip()[2:]) + "</li>")
            continue
        if in_list:
            out.append("</ul>")
            in_list = False
        m = re.match(r"^(#{1,6})\s+(.*)$", line)
        if m:
            level = len(m.group(1))
            out.append(f"<h{level}>{inline(m.group(2))}</h{level}>")
        elif line.strip() == "":
            out.append("")
        elif line.strip() == "---":
            out.append("<hr/>")
        else:
            out.append("<p>" + inline(line) + "</p>")
    if in_list:
        out.append("</ul>")
    body = "\n".join(out)
    return (
        "<!doctype html><html><head><meta charset='utf-8'>"
        "<style>body{font:16px/1.6 system-ui,sans-serif;max-width:42rem;"
        "margin:3rem auto;padding:0 1rem;color:#16161f}h1,h2,h3{line-height:1.2}"
        "hr{border:none;border-top:1px solid #1e1e2e;margin:2rem 0}</style>"
        f"</head><body>{body}</body></html>"
    )


def inline(text: str) -> str:
    text = html.escape(text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", text)
    text = re.sub(r"(?<!\*)\*(?!\*)(.+?)\*", r"<em>\1</em>", text)
    text = re.sub(r"`(.+?)`", r"<code>\1</code>", text)
    return text


def to_pdf(md: str, out_path: str) -> int:
    try:
        from reportlab.lib.pagesizes import A4
        from reportlab.lib.styles import getSampleStyleSheet
        from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer
    except ImportError:
        sys.stderr.write(
            "PDF export needs reportlab (pip install reportlab). "
            "Export to html instead, or install the dependency.\n"
        )
        return 2
    styles = getSampleStyleSheet()
    doc = SimpleDocTemplate(out_path, pagesize=A4)
    flow = []
    for line in md.splitlines():
        m = re.match(r"^(#{1,6})\s+(.*)$", line)
        if m:
            flow.append(Paragraph(m.group(2), styles[f"Heading{min(len(m.group(1)), 4)}"]))
        elif line.strip():
            flow.append(Paragraph(line, styles["BodyText"]))
        else:
            flow.append(Spacer(1, 8))
    doc.build(flow)
    return 0


def main() -> int:
    p = argparse.ArgumentParser(description="Render a markdown draft.")
    p.add_argument("--in", dest="src", required=True)
    p.add_argument("--format", required=True, choices=["md", "html", "pdf"])
    p.add_argument("--out", help="Output path; defaults next to the source.")
    args = p.parse_args()

    md = open(args.src, encoding="utf-8").read()
    base, _ = os.path.splitext(args.src)
    out = args.out or f"{base}.{args.format}"

    if args.format == "md":
        open(out, "w", encoding="utf-8").write(md)
    elif args.format == "html":
        open(out, "w", encoding="utf-8").write(md_to_html(md))
    else:
        rc = to_pdf(md, out)
        if rc != 0:
            return rc
    sys.stdout.write(f"Wrote {out}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
