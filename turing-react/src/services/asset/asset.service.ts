import axios from "axios";
import type { TurAssetItem } from "@/models/asset/asset-item.model";
import type { TurAssetTrainingStatus } from "@/models/asset/asset-training-status.model";

export class TurAssetService {
  async query(prefix = ""): Promise<TurAssetItem[]> {
    const response = await axios.get<TurAssetItem[]>("/asset", {
      params: { prefix },
    });
    return response.data;
  }

  async upload(files: File[], prefix = ""): Promise<void> {
    const formData = new FormData();
    for (const file of files) {
      formData.append("files", file);
    }
    await axios.post("/asset", formData, {
      params: { prefix },
      headers: { "Content-Type": "multipart/form-data" },
    });
  }

  async createFolder(path: string): Promise<void> {
    await axios.post("/asset/folder", null, { params: { path } });
  }

  async delete(objectName: string): Promise<void> {
    await axios.delete("/asset", { params: { objectName } });
  }

  downloadUrl(objectName: string): string {
    return `${axios.defaults.baseURL ?? ""}/asset/download?objectName=${encodeURIComponent(objectName)}`;
  }

  previewUrl(objectName: string): string {
    return `${axios.defaults.baseURL ?? ""}/asset/preview?objectName=${encodeURIComponent(objectName)}`;
  }

  async metadata(objectName: string): Promise<TurAssetItem> {
    const response = await axios.get<TurAssetItem>("/asset/metadata", {
      params: { objectName },
    });
    return response.data;
  }

  async startTraining(): Promise<TurAssetTrainingStatus> {
    const response = await axios.post<TurAssetTrainingStatus>("/asset/train");
    return response.data;
  }

  async getTrainingStatus(): Promise<TurAssetTrainingStatus> {
    const response = await axios.get<TurAssetTrainingStatus>("/asset/train/status");
    return response.data;
  }

  async getTrainingRecords(objectNames: string[]): Promise<Record<string, string>> {
    const response = await axios.get<Record<string, string>>("/asset/train/records", {
      params: { objectNames: objectNames.join(",") },
      paramsSerializer: { indexes: null },
    });
    return response.data;
  }
}
