import axios from "axios";
import type { NativeToolGroup } from "@/models/genai/native-tool.model.ts";

export class TurNativeToolService {
  async query(): Promise<NativeToolGroup[]> {
    const response = await axios.get<NativeToolGroup[]>("/native-tool");
    return response.data;
  }
}
