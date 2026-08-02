---
"@viglet/turing-sdk": minor
"@viglet/turing-react-sdk": minor
"@viglet/turing-react-ui": patch
---

T864 — the link-free response is finally link-free.

The `STRUCTURED` search response dropped every href except one: each document's
`metadata[]` — its facet chips — still carried `{href, text}` pointing back at
`/api/sn/{site}/search?…&fq[]=…`. A consumer rendering those chips was still
parsing URLs, which is the whole bug class this contract exists to remove.

**What changed.** In the structured response, a document's `metadata` entries are
now `{ field, value, filterQuery, text }` — the same shape as a structured facet
item, because it is the same action: add `filterQuery` to `request.fq` and
re-send. New types: `TurStructuredDocumentMetadata`, `TurStructuredDocument`,
`TurStructuredResults`.

**What you may need to do.** If you render `doc.raw.metadata` and read `href`,
narrow first — `"href" in chip` is the legacy shape, `"filterQuery" in chip` is
the link-free one. `ResolvedDocument.raw` is now
`TurDocument | TurStructuredDocument`; everything the resolver produces
(`url`/`title`/`description`/`date`/`image`/`text`) is unchanged, and
`resolveDocuments` accepts either response exactly as before.

react-ui's `TuringRawDocument.metadata` widened to the same union. `text` is
common to both shapes, so a component that only renders the chip label needs no
change.

The legacy (`postNative: false`) response is untouched: it still carries
`{href, text}`, byte for byte.
