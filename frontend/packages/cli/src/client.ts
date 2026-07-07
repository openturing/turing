/**
 * Minimal Turing HTTP/SSE client built on Node 22+'s global {@code fetch}.
 *
 * <p>Adapted from {@code @viglet/turing-flow-dsl}'s deploy client. Adds:
 * <ul>
 *   <li>Dev-token auth ({@code Key: <token>} header — see {@code TurDevToken})
 *       as the preferred unattended/CI path, alongside HTTP Basic for
 *       interactive use.</li>
 *   <li>{@link #streamChat} — POSTs a chat turn and parses the
 *       {@code text/event-stream} reply token-by-token.</li>
 *   <li>{@link #streamSse} — opens a GET SSE channel (chat-event tail) and
 *       relays each decoded event until aborted.</li>
 *   <li>{@link #postMultipart} — uploads a file part (skill ZIP import).</li>
 * </ul>
 *
 * <p>Carries the {@code XSRF-TOKEN} cookie + matching header across requests
 * so the CSRF filter on the write endpoints is satisfied regardless of which
 * auth mode is in play.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.6
 */

/** Either a dev token ({@code Key} header) or HTTP Basic credentials. */
export type TuringAuth =
  | { kind: "token"; token: string }
  | { kind: "basic"; username: string; password: string };

export class HttpError extends Error {
  constructor(message: string, public readonly status: number) {
    super(message);
    this.name = "HttpError";
  }
}

/** One Server-Sent Event payload decoded from a `data:` line. */
export interface SseEvent {
  /** The concatenated `data:` lines for one event, already trimmed. */
  data: string;
}

export class TuringClient {
  private csrfToken: string | null = null;
  private csrfHeaderName = "X-XSRF-TOKEN";
  private sessionCookie: string | null = null;
  private readonly authHeader: string;
  private readonly authHeaderName: string;

  constructor(
    private readonly baseUrl: string,
    auth: TuringAuth,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {
    if (auth.kind === "token") {
      this.authHeaderName = "Key";
      this.authHeader = auth.token;
    } else {
      this.authHeaderName = "Authorization";
      this.authHeader = "Basic " + Buffer.from(`${auth.username}:${auth.password}`).toString("base64");
    }
  }

  /**
   * First call — fetches a CSRF token AND establishes a session cookie.
   * Subsequent state-changing calls reuse both. Works for either auth mode:
   * the auth header is sent on this call so the server binds the session to
   * the authenticated principal.
   */
  async authenticate(): Promise<void> {
    const response = await this.fetchImpl(this.url("/api/csrf"), {
      method: "GET",
      headers: { [this.authHeaderName]: this.authHeader, Accept: "application/json" },
    });
    if (!response.ok) {
      throw new HttpError(
        `Authentication failed (${response.status} ${response.statusText}). Check your token / credentials.`,
        response.status,
      );
    }
    this.captureSession(response);
    const body = (await response.json()) as { token: string; headerName?: string };
    this.csrfToken = body.token;
    if (body.headerName) this.csrfHeaderName = body.headerName;
  }

  async get<T>(path: string): Promise<T> {
    const response = await this.fetchImpl(this.url(path), { method: "GET", headers: this.headers() });
    return this.handle<T>(response, "GET", path);
  }

  async post<T>(path: string, body: unknown): Promise<T> {
    const response = await this.fetchImpl(this.url(path), {
      method: "POST",
      headers: { ...this.headers(), "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    return this.handle<T>(response, "POST", path);
  }

  async put<T>(path: string, body: unknown): Promise<T> {
    const response = await this.fetchImpl(this.url(path), {
      method: "PUT",
      headers: { ...this.headers(), "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    return this.handle<T>(response, "PUT", path);
  }

  /** Uploads a single file part as {@code multipart/form-data}. */
  async postMultipart<T>(path: string, partName: string, fileName: string, bytes: Uint8Array,
    contentType = "application/zip"): Promise<T> {
    const form = new FormData();
    form.append(partName, new Blob([bytes as BlobPart], { type: contentType }), fileName);
    const response = await this.fetchImpl(this.url(path), {
      method: "POST",
      headers: this.headers(), // do NOT set Content-Type — fetch adds the multipart boundary
      body: form,
    });
    return this.handle<T>(response, "POST", path);
  }

  /**
   * POSTs a chat turn and invokes {@code onEvent} for every decoded SSE
   * payload. Resolves when the stream ends. The body is sent as JSON and the
   * {@code Accept} header requests {@code text/event-stream}.
   */
  async streamChat(path: string, body: unknown, onEvent: (data: string) => void,
    signal?: AbortSignal): Promise<void> {
    const response = await this.fetchImpl(this.url(path), {
      method: "POST",
      headers: { ...this.headers(), "Content-Type": "application/json", Accept: "text/event-stream" },
      body: JSON.stringify(body),
      signal,
    });
    if (!response.ok || !response.body) {
      const detail = await response.text().catch(() => "");
      throw new HttpError(
        `POST ${path} failed (${response.status} ${response.statusText})${detail ? ": " + truncate(detail, 240) : ""}`,
        response.status,
      );
    }
    this.captureSession(response);
    await consumeSse(response.body, onEvent, signal);
  }

  /** Opens a GET SSE channel and relays each decoded event until aborted. */
  async streamSse(path: string, onEvent: (data: string) => void, signal?: AbortSignal): Promise<void> {
    const response = await this.fetchImpl(this.url(path), {
      method: "GET",
      headers: { ...this.headers(), Accept: "text/event-stream" },
      signal,
    });
    if (!response.ok || !response.body) {
      throw new HttpError(`GET ${path} failed (${response.status} ${response.statusText})`, response.status);
    }
    this.captureSession(response);
    await consumeSse(response.body, onEvent, signal);
  }

  private url(path: string): string {
    return `${this.baseUrl}${path.startsWith("/") ? path : "/" + path}`;
  }

  private headers(): Record<string, string> {
    const h: Record<string, string> = { [this.authHeaderName]: this.authHeader, Accept: "application/json" };
    if (this.csrfToken) h[this.csrfHeaderName] = this.csrfToken;
    if (this.sessionCookie) h.Cookie = this.sessionCookie;
    return h;
  }

  private async handle<T>(response: Response, method: string, path: string): Promise<T> {
    this.captureSession(response);
    if (!response.ok) {
      const detail = await response.text().catch(() => "");
      throw new HttpError(
        `${method} ${path} failed (${response.status} ${response.statusText})${detail ? ": " + truncate(detail, 240) : ""}`,
        response.status,
      );
    }
    if (response.status === 204) return undefined as T;
    const text = await response.text();
    if (!text) return undefined as T;
    const ct = response.headers.get("content-type") ?? "";
    if (ct.includes("application/json")) return JSON.parse(text) as T;
    return text as unknown as T;
  }

  /** Raw byte download (e.g. an export ZIP). */
  async getBytes(path: string): Promise<Uint8Array> {
    const response = await this.fetchImpl(this.url(path), { method: "GET", headers: this.headers() });
    this.captureSession(response);
    if (!response.ok) {
      throw new HttpError(`GET ${path} failed (${response.status} ${response.statusText})`, response.status);
    }
    return new Uint8Array(await response.arrayBuffer());
  }

  private captureSession(response: Response): void {
    const getSetCookie = (response.headers as unknown as { getSetCookie?: () => string[] }).getSetCookie;
    const cookies: string[] = typeof getSetCookie === "function" ? getSetCookie.call(response.headers) : [];
    if (cookies.length === 0) return;
    const parsed = new Map<string, string>();
    if (this.sessionCookie) {
      for (const pair of this.sessionCookie.split("; ")) {
        const [k, v] = splitOnce(pair, "=");
        if (k && v !== undefined) parsed.set(k, v);
      }
    }
    for (const raw of cookies) {
      const [pair] = raw.split(";");
      if (!pair) continue;
      const [k, v] = splitOnce(pair, "=");
      if (k && v !== undefined) parsed.set(k, v);
      if (k === "XSRF-TOKEN" && v) this.csrfToken = decodeURIComponent(v);
    }
    this.sessionCookie = [...parsed.entries()].map(([k, v]) => `${k}=${v}`).join("; ");
  }
}

/**
 * Reads a {@code text/event-stream} body and invokes {@code onEvent} once per
 * SSE event, passing the concatenated (trimmed) {@code data:} payload. Comment
 * lines ({@code :heartbeat}) and field lines other than {@code data:} are
 * ignored. Exported for unit tests.
 */
export async function consumeSse(
  body: ReadableStream<Uint8Array>,
  onEvent: (data: string) => void,
  signal?: AbortSignal,
): Promise<void> {
  const reader = body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let dataLines: string[] = [];
  const flush = () => {
    if (dataLines.length > 0) {
      const data = dataLines.join("\n").trim();
      dataLines = [];
      if (data) onEvent(data);
    }
  };
  try {
    for (;;) {
      if (signal?.aborted) break;
      const { value, done } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let nl: number;
      while ((nl = buffer.indexOf("\n")) !== -1) {
        let line = buffer.slice(0, nl);
        buffer = buffer.slice(nl + 1);
        if (line.endsWith("\r")) line = line.slice(0, -1);
        if (line === "") {
          flush(); // blank line terminates an event
        } else if (line.startsWith(":")) {
          // comment / heartbeat — ignore
        } else if (line.startsWith("data:")) {
          dataLines.push(line.slice(5).replace(/^ /, ""));
        }
        // other SSE fields (event:, id:, retry:) are not used here
      }
    }
    flush();
  } finally {
    try {
      await reader.cancel();
    } catch {
      // best effort
    }
  }
}

function splitOnce(input: string, sep: string): [string, string | undefined] {
  const idx = input.indexOf(sep);
  if (idx === -1) return [input, undefined];
  return [input.slice(0, idx).trim(), input.slice(idx + 1)];
}

function truncate(s: string, max: number): string {
  return s.length <= max ? s : s.slice(0, max) + "…";
}
