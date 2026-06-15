#!/usr/bin/env python3
"""Send indexing test payloads to Turing Semantic Navigation import API.

Usage example:
  python turing_index_test.py \
    --base-url http://localhost:2700 \
    --site Portal \
    --locale pt_BR \
    --json-file ./doc.json \
    --specs-file ./field_specs.json \
    --header "Key: <your-api-key>"

Field specs file (--specs-file) is optional. When provided it must be a JSON
array of TurSNAttributeSpec objects with at least a "name" and a "type" field.
Valid types: INT, LONG, STRING, TEXT, ARRAY, DATE, BOOL, FLOAT, DOUBLE, CURRENCY.
"""

from __future__ import annotations

import argparse
import json
import ssl
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Index one or many JSON documents into Turing Semantic Navigation "
            "using POST /api/sn/import."
        )
    )
    parser.add_argument(
        "--base-url",
        default="http://localhost:2700",
        help="Base URL of Turing server (default: %(default)s)",
    )
    parser.add_argument("--site", required=True, help="Semantic Navigation site name")
    parser.add_argument(
        "--locale",
        required=True,
        help="Locale to index with (for example: en_US, pt_BR, es_ES)",
    )
    parser.add_argument(
        "--json-file",
        required=True,
        type=Path,
        help="Path to JSON file containing key/value fields",
    )
    parser.add_argument(
        "--specs-file",
        type=Path,
        help=(
            "Path to JSON file with TurSNAttributeSpec field definitions (optional). "
            "Must be a JSON array of objects with at least 'name' and 'type'. "
            "Valid types: INT, LONG, STRING, TEXT, ARRAY, DATE, BOOL, FLOAT, DOUBLE, CURRENCY."
        ),
    )
    parser.add_argument(
        "--action",
        default="CREATE",
        choices=["CREATE", "DELETE", "COMMIT"],
        help="TurSNJobAction value to send (default: %(default)s)",
    )
    parser.add_argument(
        "--token",
        help="Bearer token for Authorization header (optional)",
    )
    parser.add_argument(
        "--header",
        action="append",
        default=[],
        help="Extra header in format 'Name: Value'. Can be used multiple times.",
    )
    parser.add_argument(
        "--timeout",
        type=int,
        default=30,
        help="HTTP timeout in seconds (default: %(default)s)",
    )
    parser.add_argument(
        "--insecure",
        action="store_true",
        help="Disable TLS certificate verification (for local test only)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print generated payload without sending request",
    )
    return parser.parse_args()


def _fail(message: str, exit_code: int = 1) -> None:
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(exit_code)


def load_documents(json_file: Path) -> list[dict[str, Any]]:
    if not json_file.exists():
        _fail(f"JSON file not found: {json_file}")

    try:
        with json_file.open("r", encoding="utf-8") as file:
            data = json.load(file)
    except json.JSONDecodeError as exc:
        _fail(f"Invalid JSON in {json_file}: {exc}")

    if isinstance(data, dict):
        docs = [data]
    elif isinstance(data, list):
        docs = data
    else:
        _fail("JSON file must contain either an object or an array of objects")

    if not docs:
        _fail("JSON file does not contain any document")

    normalized_docs: list[dict[str, Any]] = []
    for index, item in enumerate(docs, start=1):
        if not isinstance(item, dict):
            _fail(f"Document #{index} is not an object")
        if "id" not in item:
            _fail(
                (
                    f"Document #{index} does not contain 'id'. "
                    "Turing requires attributes.id to process indexing."
                )
            )
        normalized_docs.append(item)

    return normalized_docs


_VALID_SPEC_TYPES = frozenset(
    {"INT", "LONG", "STRING", "TEXT", "ARRAY", "DATE", "BOOL", "FLOAT", "DOUBLE", "CURRENCY"}
)


def load_specs(specs_file: Path) -> list[dict[str, Any]]:
    if not specs_file.exists():
        _fail(f"Specs file not found: {specs_file}")

    try:
        with specs_file.open("r", encoding="utf-8") as file:
            data = json.load(file)
    except json.JSONDecodeError as exc:
        _fail(f"Invalid JSON in {specs_file}: {exc}")

    if isinstance(data, dict):
        specs = [data]
    elif isinstance(data, list):
        specs = data
    else:
        _fail("Specs file must contain either an object or an array of objects")

    for index, spec in enumerate(specs, start=1):
        if not isinstance(spec, dict):
            _fail(f"Spec #{index} is not an object")
        if "name" not in spec:
            _fail(f"Spec #{index} is missing required 'name' field")
        if "type" in spec and spec["type"] not in _VALID_SPEC_TYPES:
            valid = ", ".join(sorted(_VALID_SPEC_TYPES))
            _fail(f"Spec #{index} has invalid type '{spec['type']}'. Valid types: {valid}")

    return specs


def build_payload(
    site: str,
    locale: str,
    action: str,
    documents: list[dict[str, Any]],
    specs: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    items = []
    for attributes in documents:
        item: dict[str, Any] = {
            "locale": locale,
            "turSNJobAction": action,
            "siteNames": [site],
            "attributes": attributes,
        }
        if specs:
            item["specs"] = specs
        items.append(item)
    return {"turingDocuments": items}


def build_headers(args: argparse.Namespace) -> dict[str, str]:
    headers = {
        "Accept": "application/json",
        "Content-Type": "application/json; charset=utf-8",
    }

    if args.token:
        headers["Authorization"] = f"Bearer {args.token}"

    for raw_header in args.header:
        if ":" not in raw_header:
            _fail(f"Invalid --header format: {raw_header}")
        name, value = raw_header.split(":", 1)
        headers[name.strip()] = value.strip()

    return headers


def post_import(
    base_url: str,
    payload: dict[str, Any],
    headers: dict[str, str],
    timeout: int,
    insecure: bool,
) -> tuple[int, str]:
    endpoint = f"{base_url.rstrip('/')}/api/sn/import"
    body = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(endpoint, data=body, headers=headers, method="POST")

    context: ssl.SSLContext | None = None
    if insecure:
        context = ssl._create_unverified_context()

    try:
        with urllib.request.urlopen(request, timeout=timeout, context=context) as response:
            response_text = response.read().decode("utf-8", errors="replace").strip()
            return response.getcode(), response_text
    except urllib.error.HTTPError as exc:
        error_body = exc.read().decode("utf-8", errors="replace").strip()
        return exc.code, error_body
    except urllib.error.URLError as exc:
        _fail(f"Network error while calling Turing API: {exc}")


def main() -> int:
    args = parse_args()
    documents = load_documents(args.json_file)
    specs: list[dict[str, Any]] | None = None
    if args.specs_file:
        specs = load_specs(args.specs_file)
    payload = build_payload(args.site, args.locale, args.action, documents, specs)

    if args.dry_run:
        print(json.dumps(payload, indent=2, ensure_ascii=False))
        return 0

    headers = build_headers(args)
    status_code, response_text = post_import(
        base_url=args.base_url,
        payload=payload,
        headers=headers,
        timeout=args.timeout,
        insecure=args.insecure,
    )

    print(f"POST {args.base_url.rstrip('/')}/api/sn/import -> HTTP {status_code}")
    print(f"Response: {response_text}")

    if status_code != 200:
        return 2

    normalized_response = response_text.strip().lower()
    if normalized_response in {"true", "\"true\""}:
        print(f"Index request queued successfully for {len(documents)} document(s).")
        return 0

    print("Request reached API, but response was not 'true'.")
    return 3


if __name__ == "__main__":
    raise SystemExit(main())
