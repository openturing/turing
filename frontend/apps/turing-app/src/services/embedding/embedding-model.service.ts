import axios from "axios";
import type { TurEmbeddingModel } from "@/models/embedding/embedding-model.model.ts";
import type { TurModelVerifyResult } from "@/models/verify.model.ts";

export class TurEmbeddingModelService {
  async query(): Promise<TurEmbeddingModel[]> {
    const response = await axios.get<TurEmbeddingModel[]>("/embedding-model");
    return response.data;
  }
  async get(id: string): Promise<TurEmbeddingModel> {
    const response = await axios.get<TurEmbeddingModel>(`/embedding-model/${id}`);
    return response.data;
  }
  async structure(): Promise<TurEmbeddingModel> {
    const response = await axios.get<TurEmbeddingModel>("/embedding-model/structure");
    return response.data;
  }
  async create(model: TurEmbeddingModel): Promise<TurEmbeddingModel> {
    const response = await axios.post<TurEmbeddingModel>("/embedding-model", model);
    return response.data;
  }
  async update(model: TurEmbeddingModel): Promise<TurEmbeddingModel> {
    const response = await axios.put<TurEmbeddingModel>(
      `/embedding-model/${model.id}`,
      model
    );
    return response.data;
  }
  async delete(model: TurEmbeddingModel): Promise<boolean> {
    const response = await axios.delete(`/embedding-model/${model.id}`);
    return response.status === 200;
  }
  /** Fires a live embedding probe against the saved model to confirm it works. */
  async verify(id: string): Promise<TurModelVerifyResult> {
    const response = await axios.post<TurModelVerifyResult>(`/embedding-model/${id}/verify`);
    return response.data;
  }
}
