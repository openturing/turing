/**
 * HTTP client for the Turing ES vanilla SDK.
 *
 * <p>The React SDK delegates every call to the host application's globally
 * configured `axios` instance (inheriting baseURL, credentials, and the CSRF
 * interceptor). A framework-agnostic SDK has no such global, so this module
 * introduces an explicit {@link TuringClient}: it owns the {@code baseURL},
 * issues `fetch` requests with {@code credentials: "include"}, and applies the
 * CSRF token to every mutating call.
 *
 * <p><b>CSRF</b>: Spring Security's {@code CookieCsrfTokenRepository} writes the
 * {@code XSRF-TOKEN} cookie as HttpOnly and surfaces the token via the
 * {@code X-XSRF-TOKEN} response header. JS therefore cannot read the cookie —
 * a fetch POST that relies on the cookie alone gets a 403. {@link ensureCsrfToken}
 * primes the token with a best-effort {@code GET {baseURL}/csrf} and reads the
 * response header, exactly mirroring the React SDK's `ensureXsrfToken`. On a
 * public/anonymous deployment where {@code /csrf} is absent or CSRF is
 * disabled, the prime fails quietly and the request proceeds tokenless.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

import { assertOk, isAbort, TuringTimeoutError, TuringTransportError } from "./errors";

export interface TuringClientConfig {
  /**
   * Absolute base URL of the Turing API, e.g. {@code "http://localhost:2700/api"}.
   * Required — unlike the React SDK, there is no global axios default to fall
   * back to. A trailing slash is trimmed.
   */
  readonly baseURL: string;
  /**
   * Fetch credentials mode. Defaults to {@code "include"} so the session and
   * CSRF cookies travel with cross-origin requests (the EDS site and the
   * Turing API are typically on different hosts).
   */
  readonly credentials?: RequestCredentials;
  /** Extra headers merged into every request (e.g. an API gateway key). */
  readonly headers?: Readonly<Record<string, string>>;
  /**
   * Turing Developer Token for server-to-server (or otherwise unauthenticated)
   * callers. Sent verbatim in the {@code Key} request header, which Turing's
   * {@code TurAuthTokenHeaderFilter} resolves to the user who minted the token —
   * so protected endpoints (e.g. {@code POST /v2/ai-agent/{id}/chat}) authorize
   * without a browser session. Create one under admin → Developer Token.
   *
   * <p>Merged into {@link headers}; an explicit {@code Key} entry in
   * {@link headers} takes precedence. Has no effect on CSRF — a token-authed
   * call still relies on the endpoint being CSRF-exempt or a primed token.
   */
  readonly apiKey?: string;
  /**
   * Overrides CSRF token resolution. Return {@code undefined} to send the
   * request without a token. When omitted, the built-in {@link ensureCsrfToken}
   * logic is used (read cookie → prime via {@code /csrf}).
   */
  readonly getCsrfToken?: () => string | undefined | Promise<string | undefined>;
  /**
   * T1079 — upper bound, in milliseconds, on every request this client makes.
   * When it elapses the request is aborted and the call rejects with a
   * {@link TuringTimeoutError}, so a server-rendered page can show its
   * unavailable state instead of waiting on a stalled Turing.
   *
   * A JSON call ({@link TuringClient.request} and the API functions) is bounded
   * until its body is read; a raw call ({@link TuringClient.fetchRaw}, used by the
   * SSE helpers) until the response headers arrive, so a stream that has started
   * is never cut. Omitted or not positive: no limit, as before.
   */
  readonly timeoutMs?: number;
}

/**
 * T1079 — per-call options the API functions take as their last argument.
 */
export interface TuringCallOptions {
  /** Aborts the call; an abort rejects with the signal's own `AbortError`. */
  readonly signal?: AbortSignal;
}

export interface TuringRequestOptions {
  /** JSON body (objects are serialized) or a {@link FormData}/{@link Blob}. */
  readonly body?: unknown;
  readonly headers?: Record<string, string>;
  readonly signal?: AbortSignal;
  /**
   * Whether to attach the CSRF token. Defaults to {@code true} for mutating
   * methods (POST/PUT/PATCH/DELETE) and {@code false} for GET/HEAD.
   */
  readonly csrf?: boolean;
}

export interface TuringClient {
  /** Normalized base URL (no trailing slash). */
  readonly baseURL: string;
  /** Resolved credentials mode. */
  readonly credentials: RequestCredentials;
  /** Issues a request and parses the JSON response (throws on non-2xx). */
  request<T>(
    method: string,
    path: string,
    options?: TuringRequestOptions,
  ): Promise<T>;
  /** Convenience GET that parses JSON. */
  get<T>(path: string, options?: TuringRequestOptions): Promise<T>;
  /** Convenience POST that parses JSON. */
  post<T>(path: string, body?: unknown, options?: TuringRequestOptions): Promise<T>;
  /** Convenience DELETE that parses JSON. */
  del<T>(path: string, options?: TuringRequestOptions): Promise<T>;
  /**
   * Low-level fetch that applies {@code baseURL}, credentials, merged headers,
   * and (optionally) the CSRF token, but returns the raw {@link Response} so
   * the caller can consume a streaming body (used by the SSE chat helpers).
   */
  fetchRaw(path: string, init?: RequestInit, csrf?: boolean): Promise<Response>;
  /** Resolves the CSRF token (cookie → prime via {@code /csrf}). */
  ensureCsrfToken(): Promise<string | undefined>;
}

const MUTATING = new Set(["POST", "PUT", "PATCH", "DELETE"]);

function trimTrailingSlash(url: string): string {
  return url.endsWith("/") ? url.slice(0, -1) : url;
}

/**
 * Reads the {@code XSRF-TOKEN} cookie, if the deployment writes it
 * non-HttpOnly. Returns {@code undefined} when absent (the common case with
 * {@code CookieCsrfTokenRepository}, which marks it HttpOnly).
 */
function readXsrfCookie(): string | undefined {
  if (typeof document === "undefined") return undefined;
  const match = /(?:^|;\s*)XSRF-TOKEN=([^;]+)/.exec(document.cookie);
  return match ? decodeURIComponent(match[1]) : undefined;
}

/** A signal that fires on the caller's abort or when `timeoutMs` elapses. */
interface Deadline {
  readonly signal: AbortSignal | undefined;
  /** Whether the timer, not the caller, aborted the request. */
  timedOut(): boolean;
  /** Stops the timer and detaches from the caller's signal. */
  dispose(): void;
}

/**
 * Combines the caller's signal with a timer, without `AbortSignal.any` /
 * `AbortSignal.timeout` (absent from older runtimes this package still targets),
 * and remembers which of the two fired so a timeout can be reported as one.
 */
function deadline(caller: AbortSignal | undefined, timeoutMs: number | undefined): Deadline {
  if (!timeoutMs || timeoutMs <= 0) {
    return { signal: caller, timedOut: () => false, dispose: () => {} };
  }
  const controller = new AbortController();
  let expired = false;
  const timer = setTimeout(() => {
    expired = true;
    controller.abort();
  }, timeoutMs);
  const forward = () => controller.abort(caller?.reason);
  if (caller?.aborted) forward();
  else caller?.addEventListener("abort", forward, { once: true });
  return {
    signal: controller.signal,
    timedOut: () => expired,
    dispose: () => {
      clearTimeout(timer);
      caller?.removeEventListener("abort", forward);
    },
  };
}

/**
 * Creates a configured {@link TuringClient}. All API functions and controllers
 * in this package take a client as their first argument.
 *
 * @example
 * ```js
 * const client = createTuringClient({ baseURL: "http://localhost:2700/api" });
 * const res = await fetchSearch(client, "my-site", { q: "hello" });
 * ```
 */
export function createTuringClient(config: TuringClientConfig): TuringClient {
  if (!config?.baseURL) {
    throw new Error("createTuringClient: `baseURL` is required");
  }
  const baseURL = trimTrailingSlash(config.baseURL);
  const credentials: RequestCredentials = config.credentials ?? "include";
  // The `Key` header carries the Dev Token; an explicit `headers.Key` wins.
  const baseHeaders: Record<string, string> = {
    ...(config.apiKey ? { Key: config.apiKey } : {}),
    ...config.headers,
  };

  async function ensureCsrfToken(signal?: AbortSignal): Promise<string | undefined> {
    if (config.getCsrfToken) return config.getCsrfToken();
    const existing = readXsrfCookie();
    if (existing) return existing;
    try {
      const res = await fetch(`${baseURL}/csrf`, { credentials, signal });
      if (res.ok) {
        return res.headers.get("X-XSRF-TOKEN") ?? readXsrfCookie();
      }
    } catch (cause) {
      // The request's own abort or timeout ends the prime and the request with it.
      if (signal?.aborted) throw cause;
      // /csrf unavailable (anonymous SPA, CSRF disabled, CORS) — fall through.
    }
    return readXsrfCookie();
  }

  /** The one place a request leaves this package, with no deadline of its own. */
  async function send(path: string, init: RequestInit, csrf: boolean): Promise<Response> {
    const headers: Record<string, string> = {
      ...baseHeaders,
      ...(init.headers as Record<string, string> | undefined),
    };
    if (csrf) {
      const token = await ensureCsrfToken(init.signal ?? undefined);
      if (token) headers["X-XSRF-TOKEN"] = token;
    }
    const url = `${baseURL}${path}`;
    // T1005 — a `fetch` rejection means the request never completed, which is a
    // different thing from a status and is handled differently. Wrapped here, in
    // the one place every call in this package goes through, so `api.ts`'s
    // functions get the typed transport failure without each restating it.
    //
    // The caller's own abort is rethrown untouched: cancellation is not a
    // failure, and a search-as-you-type controller aborts on every keystroke.
    try {
      return await fetch(url, { credentials, ...init, headers });
    } catch (cause) {
      if (isAbort(cause)) throw cause;
      throw new TuringTransportError(url, cause);
    }
  }

  /**
   * Runs `work` under the client's deadline, turning the timer's abort into a
   * {@link TuringTimeoutError}; the caller's own abort passes through untouched.
   */
  async function bounded<R>(
    path: string,
    caller: AbortSignal | undefined,
    work: (signal: AbortSignal | undefined) => Promise<R>,
  ): Promise<R> {
    const limit = deadline(caller, config.timeoutMs);
    try {
      return await work(limit.signal);
    } catch (cause) {
      if (limit.timedOut()) {
        throw new TuringTimeoutError(`${baseURL}${path}`, config.timeoutMs as number);
      }
      throw cause;
    } finally {
      limit.dispose();
    }
  }

  function fetchRaw(
    path: string,
    init: RequestInit = {},
    csrf = MUTATING.has((init.method ?? "GET").toUpperCase()),
  ): Promise<Response> {
    // Bounded until the headers arrive only: the timer is disposed once `send`
    // resolves, so a stream that has started keeps its own pace. The response
    // stays tied to the deadline's signal, which no longer fires.
    return bounded(path, init.signal ?? undefined, (signal) =>
      send(path, { ...init, signal }, csrf),
    );
  }

  async function request<T>(
    method: string,
    path: string,
    options: TuringRequestOptions = {},
  ): Promise<T> {
    return bounded(path, options.signal, (signal) =>
      readJson<T>(method, path, { ...options, signal }),
    );
  }

  async function readJson<T>(
    method: string,
    path: string,
    options: TuringRequestOptions,
  ): Promise<T> {
    const upper = method.toUpperCase();
    const headers: Record<string, string> = { ...options.headers };
    let body: BodyInit | undefined;

    if (options.body !== undefined && options.body !== null) {
      if (
        options.body instanceof FormData ||
        options.body instanceof Blob ||
        typeof options.body === "string"
      ) {
        body = options.body as BodyInit;
      } else {
        body = JSON.stringify(options.body);
        headers["Content-Type"] = "application/json";
      }
    }

    const csrf = options.csrf ?? MUTATING.has(upper);
    const res = await send(
      path,
      { method: upper, headers, body, signal: options.signal },
      csrf,
    );

    assertOk(res);
    // 204 No Content or empty body → undefined.
    if (res.status === 204) return undefined as T;
    const text = await res.text();
    if (!text) return undefined as T;
    try {
      return JSON.parse(text) as T;
    } catch {
      // Non-JSON bodies (e.g. a bare number) — return the raw text.
      return text as unknown as T;
    }
  }

  return {
    baseURL,
    credentials,
    request,
    get: (path, options) => request("GET", path, options),
    post: (path, bodyArg, options) =>
      request("POST", path, { ...options, body: bodyArg }),
    del: (path, options) => request("DELETE", path, options),
    fetchRaw,
    ensureCsrfToken: () => ensureCsrfToken(),
  };
}
