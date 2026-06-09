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

  async function ensureCsrfToken(): Promise<string | undefined> {
    if (config.getCsrfToken) return config.getCsrfToken();
    const existing = readXsrfCookie();
    if (existing) return existing;
    try {
      const res = await fetch(`${baseURL}/csrf`, { credentials });
      if (res.ok) {
        return res.headers.get("X-XSRF-TOKEN") ?? readXsrfCookie();
      }
    } catch {
      // /csrf unavailable (anonymous SPA, CSRF disabled, CORS) — fall through.
    }
    return readXsrfCookie();
  }

  async function fetchRaw(
    path: string,
    init: RequestInit = {},
    csrf = MUTATING.has((init.method ?? "GET").toUpperCase()),
  ): Promise<Response> {
    const headers: Record<string, string> = {
      ...baseHeaders,
      ...(init.headers as Record<string, string> | undefined),
    };
    if (csrf) {
      const token = await ensureCsrfToken();
      if (token) headers["X-XSRF-TOKEN"] = token;
    }
    return fetch(`${baseURL}${path}`, { credentials, ...init, headers });
  }

  async function request<T>(
    method: string,
    path: string,
    options: TuringRequestOptions = {},
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
    const res = await fetchRaw(
      path,
      { method: upper, headers, body, signal: options.signal },
      csrf,
    );

    if (!res.ok) {
      throw new Error(`HTTP ${res.status}: ${res.statusText}`);
    }
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
    ensureCsrfToken,
  };
}
