---
"@viglet/turing-react-sdk": minor
"@viglet/turing-react-ui": minor
---

T710 — the React SDK + react-ui go POST-native.

`useTuringSearch` and the URL-synced `TuringProvider` store now fetch over
`POST /sn/{site}/search/query` with a JSON body and the link-free `STRUCTURED`
response (`postNative`, default `true`), so a filter value containing
`& = + # %` or a space can no longer be mis-read as query structure. New
`structured` / `request` state plus `toggleFacet` / `clearFacet` /
`clearAllFilters` / `goToPage` / `applySpellCheck` / `searchRequest`;
`navigate(href)` is kept but `@deprecated`.

**URL sync is unchanged.** The browser URL stays the source of truth for search
state — deep-linkable, bookmarkable, shareable search URLs are a kept feature.
What moved is the *API transport*, which is where the bug class lived: facet
toggles now write filter queries into the URL from structured data instead of
parsing a server-rendered href.

`useTuringFacets` and `useTuringPagination` keep their exact public shape
(`facetGroups[].facets[].toggle`, `pages[].select`, …) and switch to structured
actions underneath, so consuming JSX needs no change.

`applyFacetAction`, `searchParamsToRequest` and `requestToSearchParams` are
re-exported from `@viglet/turing-sdk` rather than duplicated, so the
ADD/REMOVE/REPLACE semantics cannot drift between the two SDKs.

**react-ui `TuringPagination`:** `href` is now optional, `current` is honoured,
and a new `onSelectPage(page)` navigates by page number (it wins over the now
optional, deprecated `onNavigate`).

**Behaviour change (opt-out available):** POST-native, `data` is `null` and
`structured` carries the response. `documents` / `groups` / `status` / `error` /
`params` are unchanged. A consumer reading `data` or calling `navigate(href)`
should pass `postNative: false` (hook option, or on `urlSync`).
