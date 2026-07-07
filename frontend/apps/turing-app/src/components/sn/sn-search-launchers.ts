/**
 * Shared builder for the two visitor-facing SN "launch" targets — the public
 * faceted **search** UI (`/sn/{name}`) and, when GenAI is enabled, the **ANN**
 * (vector) search UI (`/ann/{name}`). Both open outside the admin. When a site
 * has more than one locale each launcher also exposes a per-locale variant
 * (`?_setlocale=xx`). This is the single source of truth so the console grid
 * and the bento detail surface the same URLs.
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
  /** Per-locale variants — empty when the site has a single locale. */
  items: SnLauncherItem[];
}

export interface SnSearchLaunchers {
  search: SnLauncher;
  /** Null when GenAI is disabled for the site (no vector index to query). */
  ann: SnLauncher | null;
}

export interface SnLaunchSite {
  name?: string;
  /** True when the site has a GenAI agent bound (ANN search is available). */
  genAiEnabled: boolean;
  locales: readonly { language: string }[];
}

export function buildSnSearchLaunchers(
  site: SnLaunchSite,
  t: (key: string) => string,
): SnSearchLaunchers {
  const name = site.name ?? "";
  const searchUrl = `/sn/${name}`;
  const annUrl = `/ann/${name}`;
  const locales = site.locales ?? [];

  const perLocale = (base: string): SnLauncherItem[] =>
    locales.length > 1
      ? locales.map((locale) => ({
          label: locale.language,
          url: `${base}?_setlocale=${locale.language}`,
        }))
      : [];

  return {
    search: { label: t("forms.localeTable.openSearch"), url: searchUrl, items: perLocale(searchUrl) },
    ann: site.genAiEnabled
      ? { label: t("ann.openAnnSearch"), url: annUrl, items: perLocale(annUrl) }
      : null,
  };
}
