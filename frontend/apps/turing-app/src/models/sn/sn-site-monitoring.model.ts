export type TurSNSiteStatus = {
  queue: number;
  documents: number;
  /** False when the search engine was unreachable (e.g. Solr down or circuit breaker open). `documents` is then best-effort. */
  searchEngineAvailable: boolean;
};
