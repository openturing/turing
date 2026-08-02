---
"@viglet/turing-sdk": minor
"@viglet/turing-react-sdk": minor
"@viglet/turing-react-ui": patch
---

T713 — the href round-trip is on notice (deprecated, not removed).

Block AV's whole point is that a client should never parse a server-rendered
search URL back into params: that round-trip is what let a facet value
containing `& = + # %` or a space be re-read as query structure. T709/T710 made
POST-native the default and T714 migrated the last consumers, so as of now
nothing in this repository calls the round-trip.

**What changed.** `parseHrefToParams` now logs a **one-time** console notice
naming the structured replacements, and is marked `@deprecated` along with
`navigate(href)` on the vanilla controller, `useTuringSearch`, the URL-synced
store, and `onNavigate` on react-ui's `TuringPagination`. Everything still
works exactly as before — this release removes nothing.

The notice lives at the single parse site, so a caller sees one line per page
rather than one per facet chip, and `navigate(href)` routes through it instead
of warning again. react-ui emits no notice at all: it only *forwards* an href
to the host, it never parses one (and it stays zero-runtime-dependency, so it
could not import the notice anyway).

**Fixed on the way through:** `useCoBrowseSearch`'s `toggle_facet` and
`clear_facets` client tools looked their target up in `store.data`, which is
`null` POST-native — so both had been failing in the *default* mode since T710.
They now resolve against `store.structured` and fall back to the legacy lookup
only when running with `postNative: false`.

**What is NOT deprecated:** the server-rendered `link` / `href` /
`cleanUpLink` / `cleanUpFacets` / spell-check `link` fields themselves. They
are what make facet pages deep-linkable, bookmarkable, SEO-crawlable and usable
without JavaScript, and they are staying — render them as real anchors. Only
the SDK *parsing* them is going away, in the next major.

**Size note:** contrary to what the T709 changeset anticipated, this does not
claw back gzip budget — a deprecation notice adds bytes rather than removing
them. Both entry budgets were raised (`turing-sdk.js` 17500 → 17700,
`turing-sdk.umd.cjs` 15500 → 15750). The reclaim happens at the actual removal.
