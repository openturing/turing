import type { TurLLMVendor } from "@/models/llm/llm-vendor.model";
import axios from "axios";

export class TurLLMVendorService {
  async query(): Promise<TurLLMVendor[]> {
    const response = await axios.get<TurLLMVendor[]>("/llm/vendor");
    return response.data;
  }
}
