---
"@viglet/turing-sdk": minor
"@viglet/turing-react-sdk": minor
---

T1077: read a document by id, whole.

`fetchStoredDocuments(site, ids, locale?)` (the vanilla SDK takes the client
first and an optional `{ signal }` last) calls the new
`GET /api/sn/{site}/search/document?id=...` endpoint. It answers every stored
field of each document, not highlighted or shortened, in the order the ids were
given; ids the site does not hold are dropped. Passing several ids fetches
several candidates in one request, so a detail page whose URL could hold more
than one id no longer has to try each with its own search. When none of the ids
is on the site the server answers 404.

`createTuringApi(client)` in the vanilla SDK binds it too.
