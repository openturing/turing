import axios from "axios"

export interface FeaturesResponse {
  storageEnabled: boolean
  ragEnabled: boolean
  gitServerEnabled: boolean
}

export class TurFeaturesService {
  async getFeatures(): Promise<FeaturesResponse> {
    const response = await axios.get<FeaturesResponse>("/features")
    return response.data
  }
}
