---
"@viglet/turing-sdk": minor
---

T1005 — the status is a field, not a substring.

Two new exports, `TuringHttpError` and `TuringTransportError`. A non-2xx throws
the first, carrying `status` as a number alongside `statusText` and `url`. A
request that produced no usable response — refused connection, offline, CORS, or
a response whose body had nothing to read — throws the second, with the
underlying failure on `cause`.

Previously every failure was `new Error(\`HTTP ${status}: ${statusText}\`)` and a
`fetch` rejection was let through untouched, so telling "the tenant is
unreachable" from "the tenant said 404" from "the body was not readable" took a
regex over `error.message` plus an `instanceof TypeError` check. A message is the
one part of an error free to be reworded, so that coupling broke consumers
silently. Match with `instanceof` instead:

```ts
try {
  await fetchSearch(client, "site", params);
} catch (err) {
  if (err instanceof TuringHttpError && err.status === 404) renderEmpty();
  else if (err instanceof TuringTransportError) scheduleRetry();
  else throw err;
}
```

**Not a behaviour change for a caller that only catches.** Both extend `Error`
and keep a human-readable `message`. The one thing that changed shape: a caller
matching on the old message text should match on `status` now.

`AbortError` from the caller's own `AbortSignal` is rethrown untouched —
cancellation is not a transport failure, and a search-as-you-type controller
aborts on every keystroke.
