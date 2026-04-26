/**
 * Lightweight shape returned by `GET /api/sn` (the SN site listing endpoint).
 *
 * Mirrors the backend `TurSNSiteListDto` record — intentionally narrower than
 * `TurSNSite` because the listing view only needs identity plus the locales
 * required to build the search-URL dropdown.
 */
export type TurSNSiteListItem = {
  id: string;
  name: string;
  description: string;
  icon?: string | null;
  searchTemplate?: string | null;
  genAiEnabled?: boolean;
  turSNSiteLocales: TurSNSiteListLocale[];
};

export type TurSNSiteListLocale = {
  language: string;
};
