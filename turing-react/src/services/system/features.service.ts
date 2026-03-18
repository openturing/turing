import axios from "axios"

export interface FeaturesResponse {
  minioEnabled: boolean
  ragEnabled: boolean
}

export class TurFeaturesService {
  async getFeatures(): Promise<FeaturesResponse> {
    const response = await axios.get<FeaturesResponse>("/features")
    return response.data
  }
}
