import type {
  TurSNManifestDeriveRequest,
  TurSNManifestResult,
  TurSNSiteManifest,
} from "@/models/sn/sn-manifest.model.ts";
import axios from "axios";

/**
 * Client for the declarative SN field-manifest endpoints.
 *
 * - {@link derive} (T387) returns a **draft** manifest inferred from a sample of
 *   a source's documents — for human review, never auto-applied.
 * - {@link provision} (T382/T386) converges the reviewed manifest into the live
 *   site idempotently (re-posting the same manifest is a no-op).
 */
export class TurSNManifestService {
  /** T387 — derive a draft manifest from sample documents. */
  async derive(request: TurSNManifestDeriveRequest): Promise<TurSNSiteManifest> {
    const response = await axios.post<TurSNSiteManifest>(
      "/sn/manifest/derive",
      request,
    );
    return response.data;
  }

  /** True when an LLM-grounded derivation is available (false = heuristic-only). */
  async deriveAvailable(): Promise<boolean> {
    const response = await axios.get<{ llmAvailable: boolean }>(
      "/sn/manifest/derive/available",
    );
    return response.data.llmAvailable;
  }

  /** T382/T386 — converge the reviewed manifest into the live site. */
  async provision(manifest: TurSNSiteManifest): Promise<TurSNManifestResult> {
    const response = await axios.post<TurSNManifestResult>(
      "/sn/manifest",
      manifest,
    );
    return response.data;
  }
}
