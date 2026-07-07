import axios from "axios";
import type {
  TurCapabilityDescriptor,
  TurInstanceCapabilityRow,
} from "@/models/genai/capability.model.ts";

/**
 * T432 / §X.18 — read-only client for the unified capability registry
 * (`GET /api/capability/registry`) and the T186 per-instance heatmap matrix
 * (`GET /api/capability/matrix`).
 */
export class TurCapabilityRegistryService {
  async query(): Promise<TurCapabilityDescriptor[]> {
    const response = await axios.get<TurCapabilityDescriptor[]>("/capability/registry");
    return response.data;
  }

  async matrix(): Promise<TurInstanceCapabilityRow[]> {
    const response = await axios.get<TurInstanceCapabilityRow[]>("/capability/matrix");
    return response.data ?? [];
  }
}
