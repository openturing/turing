/**
 * Cross-domain session cookie minted by the SDK to identify a returning
 * visitor across page reloads. The same value is reused as the chat
 * {@code conversationId}, and is intended to key future personalization
 * lookups against the host site's profile store.
 *
 * The cookie is written with {@code SameSite=None; Secure; Path=/}, which
 * is required for embedding the chat into a third-party site over HTTPS.
 * Browsers reject {@code SameSite=None} cookies on plain HTTP, except on
 * {@code localhost} where dev secure-context relaxations apply.
 *
 * <p>Ported verbatim from the React SDK's `core/session.ts` — pure browser
 * cookie logic with no framework dependency.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

export const TUR_SESSION_DEFAULT_COOKIE_NAME = "TUR_SESSION";
export const TUR_SESSION_DEFAULT_TTL_SECONDS = 2_592_000;

function escapeForRegex(value: string): string {
  return value.replaceAll(/[$()*+./?[\\\]^{|}]/g, "\\$&");
}

function readCookie(name: string): string | null {
  if (typeof document === "undefined") return null;
  const match = new RegExp(`(?:^|;\\s*)${escapeForRegex(name)}=([^;]+)`).exec(document.cookie);
  return match ? decodeURIComponent(match[1]) : null;
}

function writeCookie(name: string, value: string, ttlSeconds: number): void {
  if (typeof document === "undefined") return;
  const attrs = [
    `${name}=${encodeURIComponent(value)}`,
    "Path=/",
    `Max-Age=${Math.max(0, Math.floor(ttlSeconds))}`,
    "SameSite=None",
    "Secure",
  ];
  document.cookie = attrs.join("; ");
}

function generateSessionId(): string {
  const c = globalThis.crypto;
  if (c && typeof c.randomUUID === "function") return c.randomUUID();
  const hex = (n: number) => {
    let s = "";
    while (s.length < n) s += Math.random().toString(16).slice(2);
    return s.slice(0, n);
  };
  const variant = (8 + Math.floor(Math.random() * 4)).toString(16);
  return `${hex(8)}-${hex(4)}-4${hex(3)}-${variant}${hex(3)}-${hex(12)}`;
}

export interface TurSessionOptions {
  /** Cookie name; defaults to {@link TUR_SESSION_DEFAULT_COOKIE_NAME}. */
  readonly name?: string;
  /** Cookie lifetime in seconds; defaults to {@link TUR_SESSION_DEFAULT_TTL_SECONDS} (30 days). */
  readonly ttlSeconds?: number;
}

/**
 * Returns the current visitor's session id, minting and persisting a fresh
 * one when the cookie is absent. The TTL is re-applied on every call so an
 * active user keeps the cookie alive (sliding expiration).
 *
 * @returns the session id, or {@code null} when running outside a browser
 * (SSR) — callers should treat that as "no session yet" and skip cookie work.
 */
export function getOrCreateTurSession(options: TurSessionOptions = {}): string | null {
  if (typeof document === "undefined") return null;
  const name = options.name ?? TUR_SESSION_DEFAULT_COOKIE_NAME;
  const ttl = options.ttlSeconds ?? TUR_SESSION_DEFAULT_TTL_SECONDS;
  const existing = readCookie(name);
  if (existing) {
    writeCookie(name, existing, ttl);
    return existing;
  }
  const fresh = generateSessionId();
  writeCookie(name, fresh, ttl);
  return fresh;
}

/** Reads the current session id without minting one when absent. */
export function readTurSession(name: string = TUR_SESSION_DEFAULT_COOKIE_NAME): string | null {
  return readCookie(name);
}

/**
 * Removes the session cookie. Useful when the host site signs the visitor
 * out and wants to drop any conversation continuity tied to the old id.
 */
export function clearTurSession(name: string = TUR_SESSION_DEFAULT_COOKIE_NAME): void {
  if (typeof document === "undefined") return;
  document.cookie = `${name}=; Path=/; Max-Age=0; SameSite=None; Secure`;
}
