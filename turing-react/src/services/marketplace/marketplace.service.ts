import axios from "axios"
import type { TurMarketplaceItem } from "@/models/marketplace/marketplace-item.model"
import type { TurImportResult } from "@/models/marketplace/import-result.model"

export class TurMarketplaceService {
  async list(): Promise<TurMarketplaceItem[]> {
    const response = await axios.get<TurMarketplaceItem[]>("/marketplace")
    return response.data
  }

  async importPackage(
    downloadUrl: string,
    includeContent: boolean,
    includeTemplate: boolean
  ): Promise<TurImportResult> {
    const response = await axios.post<TurImportResult>("/marketplace/import", null, {
      params: { downloadUrl, includeContent, includeTemplate },
    })
    return response.data
  }
}
