export interface TurSiteConflict {
  id: string
  name: string
}

export interface TurImportResult {
  siteImported: boolean
  siteName: string | null
  contentDocuments: number
  contentFiles: number
  templateImported: boolean
  templateName: string | null
  templateFiles: number
  error: string | null
  conflicts: TurSiteConflict[]
  /** When false the search engine was unreachable (e.g. circuit breaker open) and content was not indexed. */
  searchEngineAvailable: boolean
}
