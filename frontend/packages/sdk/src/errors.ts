/**
 * T1005 — the two errors this SDK throws, as types a consumer can match on.
 *
 * Every non-2xx used to be `new Error(\`HTTP ${status}: ${statusText}\`)`, and a
 * `fetch` rejection was let through untouched. So the two facts a caller needs
 * in order to react — was this a transport failure or an answer, and what status
 * was it — existed only as substrings of a message. Telling "the tenant is
 * unreachable" from "the tenant said 404" from "the body was not readable" took
 * a regex over `error.message`, an `instanceof TypeError` check and an
 * `error.cause !== undefined` check, and every consumer wrote that guess again.
 *
 * A message is also the wrong place for it: it is the one part of an error that
 * is free to change, so a rewording silently breaks every consumer parsing it
 * and nothing in CI notices.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

/**
 * The request completed and the server answered with a non-2xx status.
 *
 * `status` is the field to branch on. The message stays human-readable, and
 * nothing needs to parse it.
 *
 * ```ts
 * try {
 *   await fetchSearch(client, "site", params);
 * } catch (err) {
 *   if (err instanceof TuringHttpError && err.status === 404) renderEmpty();
 *   else if (err instanceof TuringTransportError) scheduleRetry();
 *   else throw err;
 * }
 * ```
 */
export class TuringHttpError extends Error {
  /** The HTTP status the server answered with. */
  readonly status: number;
  /** The reason phrase, when the server sent one — often empty over HTTP/2. */
  readonly statusText: string;
  /** The URL that was requested, for logs that have to say which call failed. */
  readonly url: string;

  constructor(status: number, statusText: string, url: string) {
    super(`HTTP ${status}${statusText ? `: ${statusText}` : ""}`);
    // Set explicitly rather than inherited: a subclass of Error reports the base
    // name otherwise, so a log line or a Sentry group would read "Error".
    this.name = "TuringHttpError";
    this.status = status;
    this.statusText = statusText;
    this.url = url;
  }
}

/**
 * The request never completed — DNS failure, refused connection, CORS, an
 * offline device, a body that could not be read.
 *
 * Distinct from {@link TuringHttpError} because the two call for different
 * handling: a transport failure is worth retrying, an answer usually is not.
 * The underlying failure is on `cause`.
 */
export class TuringTransportError extends Error {
  /** The URL that was requested. */
  readonly url: string;

  constructor(url: string, cause?: unknown, message?: string) {
    super(message ?? `Request to ${url} did not complete: ${describe(cause)}`);
    this.name = "TuringTransportError";
    this.url = url;
    // `cause` via the standard Error option would be cleaner, but this package
    // targets ES2020, where the constructor does not accept it.
    this.cause = cause;
  }
}

/**
 * A response arrived but carried nothing to read — a streaming endpoint with a
 * null body, or a JSON endpoint that answered empty.
 *
 * Typed as a {@link TuringTransportError} rather than a third class: from a
 * caller's side it is the same decision as a refused connection — the request
 * produced no usable result and retrying is reasonable — and it is the third of
 * the three cases a consumer previously had to tell apart by reading messages.
 * The message is given explicitly, because "did not complete" would be false of
 * a response that did.
 */
export function emptyBody(url: string, what: string): TuringTransportError {
  return new TuringTransportError(url, undefined, `${what} has no readable body`);
}

function describe(cause: unknown): string {
  if (cause instanceof Error) return cause.message;
  return typeof cause === "string" ? cause : String(cause);
}

/**
 * Throw {@link TuringHttpError} when a response is not ok, and return otherwise.
 *
 * The single place the status becomes an error, so a new endpoint cannot go back
 * to throwing a plain `Error` by copying its neighbour.
 */
export function assertOk(response: Response): void {
  if (!response.ok) {
    throw new TuringHttpError(response.status, response.statusText, response.url);
  }
}

/**
 * Whether this rejection is the caller's own `AbortSignal` firing.
 *
 * Cancellation is not a transport failure, and wrapping it would break every
 * consumer that catches `AbortError` to distinguish "I cancelled this" from
 * "this broke" — a search-as-you-type controller aborts on every keystroke.
 */
export function isAbort(cause: unknown): boolean {
  return cause instanceof DOMException
    ? cause.name === "AbortError"
    : cause instanceof Error && cause.name === "AbortError";
}
