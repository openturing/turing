import axios from "axios"
import type { TurMarketplaceItem } from "@/models/marketplace/marketplace-item.model"

export class TurMarketplaceService {
  async list(): Promise<TurMarketplaceItem[]> {
    const response = await axios.get<TurMarketplaceItem[]>("/marketplace")
    return response.data
  }

  async importPackage(
    downloadUrl: string,
    includeContent: boolean,
    includeTemplate: boolean,
    onProgress?: (percent: number) => void
  ): Promise<void> {
    await axios.post("/marketplace/import", null, {
      params: { downloadUrl, includeContent, includeTemplate },
      onDownloadProgress: (event) => {
        if (onProgress && event.total) {
          onProgress(Math.round((event.loaded * 100) / event.total))
        }
      },
    })
  }
}
