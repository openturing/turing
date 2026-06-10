import axios from "axios"

export type LoggingEngine = "none" | "mongodb" | "redis"

export interface FeaturesResponse {
  storageEnabled: boolean
  ragEnabled: boolean
  gitServerEnabled: boolean
  marketplaceEnabled: boolean
  seInstanceReadOnly: boolean
  /** T317 — skill folders feature (gated on storage being configured). */
  skillsEnabled: boolean
  loggingEngine: LoggingEngine
}

export class TurFeaturesService {
  async getFeatures(): Promise<FeaturesResponse> {
    const response = await axios.get<FeaturesResponse>("/features")
    return response.data
  }
}
