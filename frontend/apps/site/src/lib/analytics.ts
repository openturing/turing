/**
 * Consent-gated Google Analytics (T1097).
 *
 * The site ships no analytics until the visitor opts in, the rule viglet.org
 * W42 set for every Viglet site (consent-only, no consent-mode pings). The gtag
 * loader lives here rather than in index.html and runs only after consent; the
 * choice persists in localStorage, so the banner shows once. The published
 * react-sdk Storybook shares this origin and reads the same key.
 */

const GA_ID = "G-XCTGNN3G4R";
export const CONSENT_KEY = "turing-analytics-consent";

/** Fired by the footer's "Cookie settings" link to reopen the banner. */
export const OPEN_CONSENT_EVENT = "turing:open-consent";

/** Fired whenever the stored choice changes, so the banner re-reads it. */
export const CONSENT_CHANGED_EVENT = "turing:consent-changed";

export type Consent = "granted" | "denied";

/** The choice for this page view when storage is blocked. */
let sessionConsent: Consent | null = null;

export function getConsent(): Consent | null {
  try {
    const v = localStorage.getItem(CONSENT_KEY);
    return v === "granted" || v === "denied" ? v : sessionConsent;
  } catch {
    return sessionConsent;
  }
}

export function setConsent(consent: Consent): void {
  sessionConsent = consent;
  try {
    localStorage.setItem(CONSENT_KEY, consent);
  } catch {
    /* storage blocked: honour the choice for this page view only */
  }
  if (consent === "granted") loadAnalytics();
  window.dispatchEvent(new Event(CONSENT_CHANGED_EVENT));
}

/** Subscribes to consent changes made here or in another tab. */
export function subscribeConsent(onChange: () => void): () => void {
  window.addEventListener(CONSENT_CHANGED_EVENT, onChange);
  window.addEventListener("storage", onChange);
  return () => {
    window.removeEventListener(CONSENT_CHANGED_EVENT, onChange);
    window.removeEventListener("storage", onChange);
  };
}

/** Lets a visitor reopen the banner to change their mind. */
export function reopenConsent(): void {
  window.dispatchEvent(new Event(OPEN_CONSENT_EVENT));
}

let loaded = false;

/** Injects gtag.js and configures GA. Idempotent; only ever called after consent. */
export function loadAnalytics(): void {
  if (loaded || typeof window === "undefined") return;
  loaded = true;

  const w = window as unknown as { dataLayer: unknown[]; gtag?: (...args: unknown[]) => void };
  const script = document.createElement("script");
  script.async = true;
  script.src = `https://www.googletagmanager.com/gtag/js?id=${GA_ID}`;
  document.head.appendChild(script);

  w.dataLayer = w.dataLayer || [];
  // gtag.js reads the Arguments object, not an array, so push `arguments`.
  w.gtag = function gtag() {
    // eslint-disable-next-line prefer-rest-params
    w.dataLayer.push(arguments);
  };
  w.gtag("js", new Date());
  w.gtag("config", GA_ID);
}

/** At startup: honour a consent already granted, load nothing otherwise. */
export function initAnalyticsFromConsent(): void {
  if (getConsent() === "granted") loadAnalytics();
}
