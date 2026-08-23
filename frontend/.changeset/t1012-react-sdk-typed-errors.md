---
"@viglet/turing-react-sdk": minor
---

T1012 — the React SDK throws the same error types as the vanilla SDK.

`TuringHttpError` and `TuringTransportError` are now re-exported from the root
and thrown by `core/api.ts`, where a non-2xx previously threw a plain
`Error("HTTP 404: Not Found")` at nine call sites. So the two facts a caller
needs — was this a transport failure or an answer, and what status was it — are
fields rather than substrings of a message:

```ts
import { TuringHttpError, TuringTransportError } from "@viglet/turing-react-sdk";

try {
  await someHook.refetch();
} catch (err) {
  if (err instanceof TuringHttpError && err.status === 404) renderEmpty();
  else if (err instanceof TuringTransportError) scheduleRetry();
  else throw err;
}
```

**The classes are re-exported, not re-declared.** `instanceof` compares
constructor identity, so this package throws the very constructors
`@viglet/turing-sdk` throws — an app using both SDKs matches one type per
failure, not two. T1005 typed the vanilla SDK and left this one behind, which
briefly meant the same HTTP status surfaced in two different shapes depending on
which SDK made the call.

**Not a behaviour change for a caller that only catches.** Both extend `Error`
and keep a readable `message`. What changed shape: a caller matching on the old
message text should read `status` instead.

A request that arrives with nothing to read — a streaming endpoint with a null
body, or a 2xx with an empty JSON body — now throws `TuringTransportError` too,
because from a caller's side that is the same decision as a refused connection.
`AbortError` from your own `AbortSignal` is rethrown untouched.
