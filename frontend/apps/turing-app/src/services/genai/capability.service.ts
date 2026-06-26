import axios from "axios";
import type { TurCapabilityDescriptor } from "@/models/genai/capability.model.ts";

/**
 * T432 / §X.18 — read-only client for the unified capability registry
 * (`GET /api/capability/registry`).
 */
export class TurCapabilityRegistryService {
  async query(): Promise<TurCapabilityDescriptor[]> {
    const response = await axios.get<TurCapabilityDescriptor[]>("/capability/registry");
    return response.data;
  }
}
