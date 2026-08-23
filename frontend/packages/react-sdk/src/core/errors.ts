import {
  TuringHttpError,
  TuringTransportError,
} from "@viglet/turing-sdk";

/**
 * T1012 — the same two thrown types as the vanilla SDK, not a second pair.
 *
 * T1005 gave `@viglet/turing-sdk` `TuringHttpError` (with a numeric `status`)
 * and `TuringTransportError`, and did not touch this package, whose `core/` is a
 * copy of the same surface and went on throwing a plain Error at nine call
 * sites. So the divergence T1005 was about started running *between* the two
 * SDKs: a React consumer still had to regex `error.message`, and a consumer
 * using both got different failure shapes for one HTTP status — which reads as a
 * difference in what happened rather than a difference in library.
 *
 * The classes are **imported**, never re-declared. That is the part that must not
 * be duplicated: `instanceof` compares constructor identity, so a second
 * `TuringHttpError` defined here would not match one thrown by the vanilla SDK,
 * and a consumer of both would be back to guessing. This package already depends
 * on `@viglet/turing-sdk` at `workspace:*` and already re-exports from it, so
 * there is nothing to add for that.
 *
 * The thrower below is local, and that is fine: it is three lines that cannot
 * drift in a way a consumer can observe, and having one per package is what
 * keeps a new endpoint here from going back to a plain Error by copying its
 * neighbour.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

export { TuringHttpError, TuringTransportError };

/** Throw {@link TuringHttpError} when a response is not ok. */
export function assertOk(response: Response): void {
  if (!response.ok) {
    throw new TuringHttpError(response.status, response.statusText, response.url);
  }
}

/**
 * A response arrived carrying nothing to read — a streaming endpoint with a null
 * body, or a JSON endpoint that answered empty.
 *
 * {@link TuringTransportError} rather than a third class, for the reason the
 * vanilla SDK gives: from a caller's side it is the same decision as a refused
 * connection, and the message is passed explicitly because "did not complete"
 * would be false of a response that did.
 */
export function emptyBody(url: string, what: string): TuringTransportError {
  return new TuringTransportError(url, undefined, `${what} has no readable body`);
}

/** Whether a rejection is the caller's own `AbortSignal` firing. */
function isAbort(cause: unknown): boolean {
  return cause instanceof DOMException
    ? cause.name === "AbortError"
    : cause instanceof Error && cause.name === "AbortError";
}

/**
 * `fetch` that reports a failed request as {@link TuringTransportError} and a
 * non-2xx as {@link TuringHttpError}, and returns the raw {@link Response} so a
 * caller can still read a streaming body.
 *
 * The caller's own abort is rethrown untouched: cancellation is not a failure,
 * and a search-as-you-type hook aborts on every keystroke.
 */
export async function fetchOrThrow(url: string, init?: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(url, init);
  } catch (cause) {
    if (isAbort(cause)) throw cause;
    throw new TuringTransportError(url, cause);
  }
  assertOk(response);
  return response;
}
