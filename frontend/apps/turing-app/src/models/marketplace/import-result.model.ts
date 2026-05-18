export interface TurSiteConflict {
  id: string
  name: string
}

export interface TurAgentConflict {
  id: string
  name: string
}

/**
 * Server-side outcome of importing the {@code agents[]} block of a bundle.
 * Null on the parent result when the ZIP didn't carry any agents.
 *
 * @since 2026.2.8
 */
export interface TurAgentImportSummary {
  imported: string[]
  skipped: string[]
  personasResolved: number
  llmInstancesResolved: number
  mcpServersResolved: number
  customToolsResolved: number
  chatFlowsImported: number
  error: string | null
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
  /** Result of the agent half of the bundle. Null when no {@code agents[]} present. */
  agentSummary: TurAgentImportSummary | null
  agentConflicts: TurAgentConflict[]
}
