#!/usr/bin/env python3
"""Atlas Store RMA eligibility + id generator.

Usage:
    python rma.py --order ATL-0001-WIL --category Electronics \
                  --purchased 2026-05-01 [--today 2026-06-29] [--opened]

Prints a JSON verdict the skill relays to the shopper. Pure stdlib so it runs in
the skill sandbox with no dependencies.
"""
import argparse
import hashlib
import json
from datetime import date, datetime

# Mirrors references/return-policy.md — keep in sync.
WINDOWS = {
    "Electronics": 30,
    "Home & Kitchen": 30,
    "Outdoors": 60,
    "Fashion": 45,
    "Toys & Games": 30,
}
RESTOCK_FEE = {"Electronics": 0.15}  # applied only when opened after 14 days


def parse_date(s: str) -> date:
    return datetime.strptime(s, "%Y-%m-%d").date()


def rma_id(order: str) -> str:
    digest = hashlib.sha1(order.encode("utf-8")).hexdigest()[:8].upper()
    return f"RMA-{digest}"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--order", required=True)
    ap.add_argument("--category", required=True)
    ap.add_argument("--purchased", required=True)
    ap.add_argument("--today")
    ap.add_argument("--opened", action="store_true")
    ap.add_argument("--final-sale", action="store_true")
    args = ap.parse_args()

    today = parse_date(args.today) if args.today else date.today()
    purchased = parse_date(args.purchased)
    days = (today - purchased).days
    window = WINDOWS.get(args.category, 30)

    if args.final_sale:
        verdict = {"eligible": False, "reason": "Final-sale items are non-returnable."}
    elif days > window:
        verdict = {
            "eligible": False,
            "reason": f"Outside the {window}-day window (purchased {days} days ago).",
        }
    else:
        fee = 0.0
        if args.category in RESTOCK_FEE and args.opened and days > 14:
            fee = RESTOCK_FEE[args.category]
        verdict = {
            "eligible": True,
            "rma": rma_id(args.order),
            "days_since_purchase": days,
            "window_days": window,
            "restocking_fee_pct": round(fee * 100, 1),
            "refund_eta": "5-7 business days after we receive the item",
            "shipping": "A prepaid return label will be emailed with the RMA.",
        }

    print(json.dumps(verdict, indent=2))


if __name__ == "__main__":
    main()
