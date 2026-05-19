#!/usr/bin/env python
"""AI-powered git commit.

Generates a commit message (short imperative title + bulleted summary body)
from the staged diff using the OpenAI Chat Completions API.

Reads the API key from OPENAI_API_KEY. No third-party dependencies.

Usage:
    python scripts/ai_commit.py              # commit staged changes
    python scripts/ai_commit.py --dry-run    # print message, do not commit
    python scripts/ai_commit.py --model gpt-4o-mini

Environment:
    OPENAI_API_KEY   required
    OPENAI_MODEL     optional, overrides default model
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

API_URL = "https://api.openai.com/v1/chat/completions"
DEFAULT_MODEL = "gpt-4o-mini"
MAX_DIFF_CHARS = 60_000

SYSTEM_PROMPT = """You are a git commit message generator.
Given a unified diff, produce a commit message with this exact shape:

<title>
<blank line>
- <bullet>
- <bullet>
- <bullet>

Rules:
- Title: imperative mood, max 72 chars, no trailing period.
- Use Conventional Commits prefix when applicable (feat:, fix:, chore:, refactor:, docs:, test:, build:, perf:).
- Body: 2 to 6 bullets summarizing the WHY and key changes; describe intent and behaviour, not a file-by-file diff replay.
- Bullets start with '- ' on their own line.
- No markdown headers, no code fences, no emojis.
Return only the commit message text."""


def run(cmd: list[str], check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        cmd,
        check=check,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )


def get_staged_diff() -> str:
    return run(["git", "diff", "--staged"], check=False).stdout


def get_staged_files() -> list[str]:
    result = run(["git", "diff", "--staged", "--name-only"], check=False)
    return [line for line in result.stdout.splitlines() if line.strip()]


def truncate(diff: str, limit: int) -> str:
    if len(diff) <= limit:
        return diff
    return diff[:limit] + "\n\n[...diff truncated for context length...]"


def call_openai(api_key: str, model: str, diff: str, files: list[str]) -> str:
    user_prompt = (
        "Files changed:\n"
        + "\n".join(f"- {f}" for f in files)
        + "\n\nUnified diff:\n"
        + diff
    )
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt},
        ],
        "temperature": 0.2,
    }
    req = urllib.request.Request(
        API_URL,
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="ignore")
        raise SystemExit(f"OpenAI API error {exc.code}: {body}")
    except urllib.error.URLError as exc:
        raise SystemExit(f"OpenAI API request failed: {exc}")
    return data["choices"][0]["message"]["content"].strip()


def main() -> int:
    parser = argparse.ArgumentParser(description="AI-powered git commit.")
    parser.add_argument("--dry-run", action="store_true", help="Print the message and exit.")
    parser.add_argument(
        "--model",
        default=os.environ.get("OPENAI_MODEL", DEFAULT_MODEL),
        help=f"OpenAI model (default: {DEFAULT_MODEL} or $OPENAI_MODEL).",
    )
    parser.add_argument(
        "--max-chars",
        type=int,
        default=MAX_DIFF_CHARS,
        help="Truncate the diff to this many chars before sending.",
    )
    args = parser.parse_args()

    api_key = os.environ.get("OPENAI_API_KEY")
    if not api_key:
        print("Error: OPENAI_API_KEY is not set.", file=sys.stderr)
        return 2

    diff = get_staged_diff()
    if not diff.strip():
        print("No staged changes. Run `git add <files>` first.", file=sys.stderr)
        return 1

    files = get_staged_files()
    message = call_openai(api_key, args.model, truncate(diff, args.max_chars), files)

    if args.dry_run:
        print(message)
        return 0

    print("--- commit message ---")
    print(message)
    print("----------------------")

    proc = subprocess.run(
        ["git", "commit", "-F", "-"],
        input=message,
        text=True,
        encoding="utf-8",
    )
    return proc.returncode


if __name__ == "__main__":
    sys.exit(main())
