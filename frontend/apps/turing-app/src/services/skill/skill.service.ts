import axios from "axios";

import type {
  TurSkill,
  TurSkillInstallResponse,
  TurSkillManifest,
  TurSkillValidateResponse,
} from "@/models/skill/skill.model";

/**
 * T116/T117 — client for the Skills marketplace API. Listing endpoints are
 * tenant-agnostic (same catalog for every admin); install/uninstall mutate the
 * installed `skill` table.
 *
 * Base URL is `/api` (axios global), so paths here are relative (`/skill`).
 *
 * @since 2026.3.1
 */
export class TurSkillService {
  /** GET /api/skill — installed skills. */
  async list(): Promise<TurSkill[]> {
    const response = await axios.get<TurSkill[]>(`/skill`);
    return response.data;
  }

  /** GET /api/skill/{id} — installed skill as a manifest. */
  async get(id: string): Promise<TurSkillManifest> {
    const response = await axios.get<TurSkillManifest>(`/skill/${id}`);
    return response.data;
  }

  /** GET /api/skill/catalog — bundled platform skills (the v1 catalog). */
  async catalog(): Promise<TurSkillManifest[]> {
    const response = await axios.get<TurSkillManifest[]>(`/skill/catalog`);
    return response.data;
  }

  /** POST /api/skill/validate — validate a manifest without installing. */
  async validate(manifest: TurSkillManifest): Promise<TurSkillValidateResponse> {
    const response = await axios.post<TurSkillValidateResponse>(`/skill/validate`, manifest);
    return response.data;
  }

  /** POST /api/skill/install — install a user-supplied manifest. */
  async install(manifest: TurSkillManifest): Promise<TurSkillInstallResponse> {
    const response = await axios.post<TurSkillInstallResponse>(`/skill/install`, manifest);
    return response.data;
  }

  /** POST /api/skill/catalog/{name}/install — install a bundled skill by name. */
  async installFromCatalog(name: string): Promise<TurSkillInstallResponse> {
    const response = await axios.post<TurSkillInstallResponse>(
      `/skill/catalog/${encodeURIComponent(name)}/install`,
    );
    return response.data;
  }

  /** DELETE /api/skill/{id} — uninstall (built-in skills are delete-protected → 409). */
  async uninstall(id: string): Promise<boolean> {
    const response = await axios.delete<boolean>(`/skill/${id}`);
    return response.data;
  }
}
