import type { TurTokenInstance } from "@/models/token/token-instance.model.ts";
import axios from "axios";

export class TurTokenInstanceService {
  async query(): Promise<TurTokenInstance[]> {
    const response = await axios.get<TurTokenInstance[]>("/dev/token");
    return response.data;
  }
  async get(id: string): Promise<TurTokenInstance> {
    const response = await axios.get<TurTokenInstance>(`/dev/token/${id}`);
    return response.data;
  }
  async create(turTokenInstance: TurTokenInstance): Promise<TurTokenInstance> {
    const response = await axios.post<TurTokenInstance>(
      "/dev/token",
      turTokenInstance,
    );
    return response.data;
  }
  async update(turTokenInstance: TurTokenInstance): Promise<TurTokenInstance> {
    const response = await axios.put<TurTokenInstance>(
      `/dev/token/${turTokenInstance.id.toString()}`,
      turTokenInstance,
    );
    return response.data;
  }
  async delete(turTokenInstance: TurTokenInstance): Promise<boolean> {
    const response = await axios.delete<TurTokenInstance>(
      `/dev/token/${turTokenInstance.id.toString()}`,
    );
    return response.status == 200;
  }
}
