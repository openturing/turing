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
}
