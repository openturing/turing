import axios from "axios"
import type { TurMarketplaceItem } from "@/models/marketplace/marketplace-item.model"
import type { TurImportResult, TurSiteConflict } from "@/models/marketplace/import-result.model"

export interface TurMarketplaceInspectResult {
  hasContent: boolean
  hasTemplate: boolean
  templateName: string | null
  conflicts: TurSiteConflict[]
}

export class TurMarketplaceService {
  async list(): Promise<TurMarketplaceItem[]> {
    const response = await axios.get<TurMarketplaceItem[]>("/marketplace")
    return response.data
  }

  async getReadme(readmeUrl: string): Promise<string> {
    const response = await axios.get<string>("/marketplace/readme", {
      params: { url: readmeUrl },
      responseType: "text",
      transformResponse: [(data) => data],
    })
    return response.data
  }

  async inspect(downloadUrl: string): Promise<TurMarketplaceInspectResult> {
    const response = await axios.get<TurMarketplaceInspectResult>("/marketplace/inspect", {
      params: { downloadUrl },
    })
    return response.data
  }

  async importPackage(
    downloadUrl: string,
    includeContent: boolean,
    includeTemplate: boolean,
    overwrite = false,
  ): Promise<TurImportResult> {
    const response = await axios.post<TurImportResult>("/marketplace/import", null, {
      params: { downloadUrl, includeContent, includeTemplate, overwrite },
    })
    return response.data
  }
}
