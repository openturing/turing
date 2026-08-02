---
"@viglet/turing-sdk": minor
---

T709 — POST-native search. `createSearchController` now drives search over
`POST /sn/{site}/search/query` with a JSON body and the link-free `STRUCTURED`
response, so a filter value containing `& = + # %` or a space can no longer be
mis-read as query structure. New `toggleFacet` / `clearFacet` /
`clearAllFilters` / `applySpellCheck` / `searchRequest` navigate from structured
state instead of parsing an href; `state.request` is the request the controller
mutates and re-sends. New exports: `postSearch`, `postStructuredSearch`,
`applyFacetAction`, `resolveStructuredGroups` and the `TurSearchRequest` /
`TurStructured*` types.

**Behaviour change (opt-out available):** POST-native is the default, so
`state.data` (the legacy link-bearing response) is `null` and `state.structured`
carries the response. `state.documents` / `groups` / `status` / `error` /
`params` are unchanged. A consumer that reads `state.data` or calls
`navigate(href)` should pass `{ postNative: false }` to keep the previous GET
behaviour, which is fully intact.

No CSRF priming round-trip: `/api/sn/**` is CSRF-exempt server-side, so the
search POST costs exactly one request, same as the GET it replaces.
