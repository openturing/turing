---
name: turing-currency-decimal
description: Reusable playbook for demands involving numeric localization (dot vs comma decimal separators), currency fields, Solr currency schema (CurrencyFieldType + currency.xml), and React admin form normalization. Use whenever a task touches currency/decimal fields, TurSEFieldType numeric types, global decimal-separator settings, Solr CurrencyFieldType, or on-blur normalization of FLOAT/DOUBLE/CURRENCY inputs.
---

# Reusable Playbook: Decimal and Currency Demands

Use this checklist whenever a demand involves numeric localization (dot/comma), currency fields, Solr schema updates, and React admin forms.

## Quick Checklist (10 lines)

1. Confirm `TurSEFieldType`/API/frontend type options are aligned.
2. Keep enum persistence as `@Enumerated(EnumType.STRING)`.
3. Map `CURRENCY` to Solr `currency` (`CurrencyFieldType`), never `pdouble`.
4. Ensure `currency.xml` exists in all configsets and packaged zip configsets.
5. Index currency as `amount,ISO4217` (example: `150.00,BRL`).
6. Use one global decimal source (`DOT`/`COMMA`) and normalize centrally.
7. Normalize decimal-capable React inputs on blur (`FLOAT`/`DOUBLE`/`CURRENCY`).
8. Preserve date-like values in mixed fields (normalize only numeric-looking strings).
9. Validate backend first (`mvn test -Dskip.npm`; in PowerShell use `"-Dskip.npm"`), then frontend (`npm run compile`).
10. For Windows npm issues, use resilient deps fallback (`npm ci` -> `npm install` -> `--legacy-peer-deps` when needed).

## 1) Backend and Persistence Rules

- Keep field enums aligned end-to-end (`TurSEFieldType`, API type lists, frontend type selectors).
- Persist enum fields as strings (`@Enumerated(EnumType.STRING)`) to avoid ordinal drift across versions.
- If introducing global numeric behavior, centralize in a dedicated service (e.g., global settings + normalizer component) and avoid scattered parsing logic.

## 2) Solr Currency Rules (Mandatory)

- Map `CURRENCY` to Solr `CurrencyFieldType` (`currency`), not `pdouble`.
- Ensure schema field type exists with these attributes:
  - `class="solr.CurrencyFieldType"`
  - `amountLongSuffix="_l_ns"`
  - `codeStrSuffix="_s_ns"`
  - `defaultCurrency="USD"`
  - `currencyConfig="currency.xml"`
- Ensure `currency.xml` exists in all configsets (`en`, `pt`, `es`, `ca`) and in packaged zip configsets used at runtime.
- Currency payload format for indexing must be `amount,ISO4217` (example: `150.00,BRL`).

## 3) Decimal Separator Behavior (Global)

- Use one global config source (`DOT` or `COMMA`) and normalize values before indexing/query persistence.
- Frontend forms should normalize on blur for decimal-capable fields (`FLOAT`, `DOUBLE`, `CURRENCY`).
- Keep date-like values untouched in mixed inputs (e.g., custom facet ranges) and only normalize when value looks numeric.

## 4) React/Admin Integration Pattern

- Add dedicated route + page for global settings.
- Expose typed model and service for `/api/system/global-settings`.
- Use a reusable hook for decimal symbol retrieval and normalization helpers.
- Update placeholders/descriptions dynamically to reflect active separator and expected currency format.

## 5) Validation Workflow

- Backend-first validation: run focused tests with `mvn test -Dskip.npm`.
- Frontend validation: prefer `npm run compile`.
- If Windows `EPERM`/lock affects `npm ci`, use resilient scripts with fallback.

## 6) Resilient npm Script Pattern

- Prefer split scripts:
  - `compile:deps` (or `build:deps`) for dependency step.
  - `compile:build` (or `build:run`) for actual build.
  - top-level `compile`/`build` chaining both.
- Recommended deps fallback pattern:
  - `npm install --no-audit --no-fund`
- For workspaces with peer-resolution instability, extend fallback with:
  - `npm install --no-audit --no-fund --legacy-peer-deps`

## 7) Delivery Discipline

- Prefer targeted fixes over broad refactors.
- Regenerate only artifacts that must reflect schema/runtime changes.
- If validation reveals unrelated pre-existing issues, report them separately and keep scope explicit.
