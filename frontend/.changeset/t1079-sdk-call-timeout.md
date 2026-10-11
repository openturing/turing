---
"@viglet/turing-sdk": minor
---

T1079: a call can be bounded.

`createTuringClient({ baseURL, timeoutMs })` puts an upper bound on every request
the client makes. When the limit elapses the request is aborted and the call
rejects with the new `TuringTimeoutError`, which extends `TuringTransportError`
and carries `timeoutMs` and `url`. A JSON call is bounded until its body is read;
a raw or streaming call (`client.fetchRaw`, the SSE helpers) only until the
response headers arrive, so a stream that has started is never cut off.

`fetchSearch`, `postSearch`, `postStructuredSearch`, `fetchAutoComplete` and
`fetchSimilar` take an optional last argument, `{ signal }`, which is passed down
to the request. An abort from that signal still rejects with its own
`AbortError`, never with a timeout or transport error.

```ts
const client = createTuringClient({ baseURL, timeoutMs: 8000 });
try {
  await postStructuredSearch(client, "site", request);
} catch (err) {
  if (err instanceof TuringTimeoutError) renderUnavailable();
  else throw err;
}
```

Without `timeoutMs` nothing changes: there is no limit and no extra signal.
