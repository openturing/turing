---
"@viglet/turing-react-sdk": minor
---

T860 — `EnrichedFacetItem.link` stops lying POST-native.

`useTuringFacets` built each item with `link: ""` in the link-free mode, because
the structured response carries no href while the type declared `link: string`.
That is a silent, plausible-looking bug on the consumer side: `<a
href={facet.link}>` compiles, renders, and links to the current page.

**What changed.** `link` is now optional (`link?: string`) and is *omitted*
rather than emptied in POST-native mode. It is still populated in legacy
(`postNative: false`) mode, where the server really does render an href.

**What you may need to do.** Nothing if you use `facet.toggle` (the documented
path). If you read `facet.link` — including in a React `key` like
`` key={facet.label + facet.link} `` — TypeScript will now tell you it can be
absent. Keys can simply use `facet.label`, which is unique within a group;
anchors need an explicit fallback, because there is genuinely no URL to link to
when the response is link-free.

The type now says what was already true of the data, which is the point: this
is the last place in the SDK where the link-free contract was papered over
instead of expressed.
