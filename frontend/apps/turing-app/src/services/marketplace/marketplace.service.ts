import axios from "axios"
import type { TurMarketplaceItem } from "@/models/marketplace/marketplace-item.model"
import type { TurImportResult, TurSiteConflict } from "@/models/marketplace/import-result.model"
import type { ContentExchangeProgress } from "@/services/sn/sn.service"

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
    taskId?: string,
  ): Promise<TurImportResult> {
    const params: Record<string, string | boolean> = {
      downloadUrl,
      includeContent,
      includeTemplate,
      overwrite,
    }
    if (taskId) params.taskId = taskId
    const response = await axios.post<TurImportResult>("/marketplace/import", null, { params })
    return response.data
  }

  async subscribeImportProgress(
    taskId: string,
    onProgress: (data: ContentExchangeProgress) => void,
    onComplete: () => void,
    onError?: (error: unknown) => void,
  ): Promise<void> {
    const baseUrl = axios.defaults.baseURL || "/api"
    try {
      const response = await fetch(
        `${baseUrl}/marketplace/import/progress/${taskId}`,
        { credentials: "include" },
      )
      if (!response.ok || !response.body) {
        onError?.(new Error(`SSE failed: ${response.status}`))
        return
      }
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ""
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const lines = buffer.split("\n")
        buffer = lines.pop() || ""
        for (const line of lines) {
          if (line.startsWith("data:")) {
            const json = line.slice(5).trim()
            if (json) {
              const data: ContentExchangeProgress = JSON.parse(json)
              onProgress(data)
              if (data.phase === "completed") {
                onComplete()
                return
              }
            }
          }
        }
      }
      onComplete()
    } catch (e) {
      onError?.(e)
    }
  }
}
