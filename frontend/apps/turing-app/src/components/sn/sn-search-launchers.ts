/**
 * Shared builder for the two visitor-facing SN "launch" targets — the public
 * faceted **search** UI (`/sn/{name}`) and, when RAG is available, the **ANN**
 * (vector) search UI (`/ann/{name}`). Both open outside the admin.
 *
 * Per-locale variants (`?_setlocale=xx`) are exposed when the site has more than
 * one locale. When the site has an SPA `searchTemplate` (and storage is on) the
 * search launcher additionally distinguishes the **template** view (`/sn/{name}`)
 * from the built-in **Default Search** (`?_embedded=true`) — mirroring the
 * per-locale table. This is the single source of truth so the console grid and
 * the bento detail surface the same URLs.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */

export interface SnLauncherItem {
  label: string;
  url: string;
}

export interface SnLauncher {
  label: string;
  /** Base URL (all locales / default). */
  url: string;
  /** Extra variants (per-locale and/or template-vs-default). Empty for a plain link. */
  items: SnLauncherItem[];
}

export interface SnSearchLaunchers {
  search: SnLauncher;
  /** Null when the site is not ANN-ready (no RAG-enabled effective agent). */
  ann: SnLauncher | null;
}

export interface SnLaunchSite {
  name?: string;
  /**
   * True when the site is ANN-ready: its own agent — or, via the T622 fallback,
   * the global Default AI Agent — is enabled with RAG on. The caller decides
   * this (it needs the global default flag from `/api/features`).
   */
  annEnabled: boolean;
  /** Object storage is configured (gates the SPA `searchTemplate` path). */
  storageEnabled?: boolean;
  /** The site's SPA search template name, when one is set. */
  searchTemplate?: string | null;
  locales: readonly { language: string }[];
}

function withParam(base: string, param: string): string {
  return `${base}${base.includes("?") ? "&" : "?"}${param}`;
}

export function buildSnSearchLaunchers(
  site: SnLaunchSite,
  t: (key: string) => string,
): SnSearchLaunchers {
  const name = site.name ?? "";
  const searchUrl = `/sn/${name}`;
  const defaultUrl = withParam(searchUrl, "_embedded=true");
  const annUrl = `/ann/${name}`;
  const locales = site.locales ?? [];
  const hasMultipleLocales = locales.length > 1;
  const hasTemplate = Boolean(site.storageEnabled && site.searchTemplate?.trim());

  const openSearchLabel = t("forms.localeTable.openSearch");
  const searchDefaultLabel = t("forms.localeTable.searchDefault");

  const perLocale = (base: string, suffix?: string): SnLauncherItem[] =>
    locales.map((locale) => ({
      label: suffix ? `${locale.language} (${suffix})` : locale.language,
      url: withParam(base, `_setlocale=${locale.language}`),
    }));

  // The launcher's base URL (`/sn/{name}`) is the SPA-template view when a
  // template exists, else the plain search. Dropdown items are the ALTERNATIVES
  // to that base: the built-in Default Search (`_embedded=true`) and/or the
  // per-locale variants — mirroring the per-locale table's choices.
  const searchItems: SnLauncherItem[] = [];
  if (hasTemplate && hasMultipleLocales) {
    searchItems.push(
      ...perLocale(searchUrl),
      ...perLocale(defaultUrl, searchDefaultLabel),
    );
  } else if (hasTemplate) {
    searchItems.push({ label: searchDefaultLabel, url: defaultUrl });
  } else if (hasMultipleLocales) {
    searchItems.push(...perLocale(searchUrl));
  }

  return {
    search: { label: openSearchLabel, url: searchUrl, items: searchItems },
    ann: site.annEnabled
      ? { label: t("ann.openAnnSearch"), url: annUrl, items: perLocale(annUrl) }
      : null,
  };
}
