import axios from "axios";
import type {
  TurKnowledgeBase,
  TurMicrothesaurus,
  TurSNSiteMicrothesaurus,
  TurSNSiteMicrothesaurusConfig,
  TurThesaurusDraft,
  TurThesaurusGenerationRequest,
  TurThesaurusSeed,
  TurThesaurusTerm,
} from "@/models/kb/thesaurus.model";

// Block AQ (§XL) — Thesaurus admin REST client. Backend routes are under
// `/kb` (internal package name); the user-facing surface is "Thesaurus".
export class TurThesaurusService {
  // ---- Knowledge Base (library) ----
  async listKnowledgeBases(): Promise<TurKnowledgeBase[]> {
    const { data } = await axios.get<TurKnowledgeBase[]>("/kb");
    return data;
  }
  async getKnowledgeBase(id: string): Promise<TurKnowledgeBase> {
    const { data } = await axios.get<TurKnowledgeBase>(`/kb/${id}`);
    return data;
  }
  async createKnowledgeBase(kb: TurKnowledgeBase): Promise<TurKnowledgeBase> {
    const { data } = await axios.post<TurKnowledgeBase>("/kb", kb);
    return data;
  }
  async updateKnowledgeBase(kb: TurKnowledgeBase): Promise<TurKnowledgeBase> {
    const { data } = await axios.put<TurKnowledgeBase>(`/kb/${kb.id}`, kb);
    return data;
  }
  async deleteKnowledgeBase(kb: TurKnowledgeBase): Promise<boolean> {
    const res = await axios.delete(`/kb/${kb.id}`);
    return res.status === 200;
  }

  // ---- Microthesaurus (one tree = language + domain) ----
  async listMicrothesauri(knowledgeBaseId: string): Promise<TurMicrothesaurus[]> {
    const { data } = await axios.get<TurMicrothesaurus[]>(
      `/kb/${knowledgeBaseId}/microthesaurus`,
    );
    return data;
  }
  async getMicrothesaurus(
    knowledgeBaseId: string,
    id: string,
  ): Promise<TurMicrothesaurus> {
    const { data } = await axios.get<TurMicrothesaurus>(
      `/kb/${knowledgeBaseId}/microthesaurus/${id}`,
    );
    return data;
  }
  async createMicrothesaurus(
    knowledgeBaseId: string,
    m: TurMicrothesaurus,
  ): Promise<TurMicrothesaurus> {
    const { data } = await axios.post<TurMicrothesaurus>(
      `/kb/${knowledgeBaseId}/microthesaurus`,
      m,
    );
    return data;
  }
  async updateMicrothesaurus(
    knowledgeBaseId: string,
    m: TurMicrothesaurus,
  ): Promise<TurMicrothesaurus> {
    const { data } = await axios.put<TurMicrothesaurus>(
      `/kb/${knowledgeBaseId}/microthesaurus/${m.id}`,
      m,
    );
    return data;
  }
  async deleteMicrothesaurus(
    knowledgeBaseId: string,
    id: string,
  ): Promise<boolean> {
    const res = await axios.delete(
      `/kb/${knowledgeBaseId}/microthesaurus/${id}`,
    );
    return res.status === 200;
  }

  // ---- Terms ----
  async listTerms(microthesaurusId: string): Promise<TurThesaurusTerm[]> {
    const { data } = await axios.get<TurThesaurusTerm[]>(
      `/kb/microthesaurus/${microthesaurusId}/term`,
    );
    return data;
  }
  async createTerm(
    microthesaurusId: string,
    term: TurThesaurusTerm,
  ): Promise<TurThesaurusTerm> {
    const { data } = await axios.post<TurThesaurusTerm>(
      `/kb/microthesaurus/${microthesaurusId}/term`,
      term,
    );
    return data;
  }
  async updateTerm(
    microthesaurusId: string,
    term: TurThesaurusTerm,
  ): Promise<TurThesaurusTerm> {
    const { data } = await axios.put<TurThesaurusTerm>(
      `/kb/microthesaurus/${microthesaurusId}/term/${term.id}`,
      term,
    );
    return data;
  }
  async deleteTerm(
    microthesaurusId: string,
    id: string,
  ): Promise<boolean> {
    const res = await axios.delete(
      `/kb/microthesaurus/${microthesaurusId}/term/${id}`,
    );
    return res.status === 200;
  }

  // ---- Seed library ----
  async listSeeds(): Promise<TurThesaurusSeed[]> {
    const { data } = await axios.get<TurThesaurusSeed[]>("/kb/seeds");
    return data;
  }
  async importSeed(
    knowledgeBaseId: string,
    seedId: string,
  ): Promise<TurMicrothesaurus> {
    const { data } = await axios.post<TurMicrothesaurus>(
      `/kb/${knowledgeBaseId}/seeds/${seedId}`,
      {},
    );
    return data;
  }

  // ---- XML authority-file upload (Turing Thesaurus Exchange) ----
  async uploadAuthorityFile(
    knowledgeBaseId: string,
    file: File,
    domain?: string,
  ): Promise<TurMicrothesaurus> {
    const form = new FormData();
    form.append("file", file);
    const query = domain ? `?domain=${encodeURIComponent(domain)}` : "";
    const { data } = await axios.post<TurMicrothesaurus>(
      `/kb/${knowledgeBaseId}/microthesaurus/import${query}`,
      form,
      { headers: { "Content-Type": "multipart/form-data" } },
    );
    return data;
  }

  // ---- LLM-assisted generation (T675) ----
  async generateDraft(
    knowledgeBaseId: string,
    request: TurThesaurusGenerationRequest,
  ): Promise<TurThesaurusDraft> {
    const { data } = await axios.post<TurThesaurusDraft>(
      `/kb/${knowledgeBaseId}/microthesaurus/generate`,
      request,
    );
    return data;
  }
  async createFromDraft(
    knowledgeBaseId: string,
    draft: TurThesaurusDraft,
  ): Promise<TurMicrothesaurus> {
    const { data } = await axios.post<TurMicrothesaurus>(
      `/kb/${knowledgeBaseId}/microthesaurus/from-draft`,
      draft,
    );
    return data;
  }

  // ---- Per-SN-site selection + index config (T670) ----
  async listSiteSelections(snSiteId: string): Promise<TurSNSiteMicrothesaurus[]> {
    const { data } = await axios.get<TurSNSiteMicrothesaurus[]>(
      `/sn/${snSiteId}/microthesaurus`,
    );
    return data;
  }
  async selectForSite(
    snSiteId: string,
    microthesaurusId: string,
  ): Promise<TurSNSiteMicrothesaurus> {
    const { data } = await axios.post<TurSNSiteMicrothesaurus>(
      `/sn/${snSiteId}/microthesaurus`,
      { microthesaurusId, enabled: true },
    );
    return data;
  }
  async setSiteSelectionEnabled(
    snSiteId: string,
    selectionId: string,
    enabled: boolean,
  ): Promise<TurSNSiteMicrothesaurus> {
    const { data } = await axios.put<TurSNSiteMicrothesaurus>(
      `/sn/${snSiteId}/microthesaurus/${selectionId}?enabled=${enabled}`,
      {},
    );
    return data;
  }
  async deselectForSite(snSiteId: string, selectionId: string): Promise<boolean> {
    const res = await axios.delete(`/sn/${snSiteId}/microthesaurus/${selectionId}`);
    return res.status === 200;
  }
  async getSiteConfig(snSiteId: string): Promise<TurSNSiteMicrothesaurusConfig> {
    const { data } = await axios.get<TurSNSiteMicrothesaurusConfig>(
      `/sn/${snSiteId}/microthesaurus/config`,
    );
    return data;
  }
  async saveSiteConfig(
    snSiteId: string,
    config: TurSNSiteMicrothesaurusConfig,
  ): Promise<TurSNSiteMicrothesaurusConfig> {
    const { data } = await axios.put<TurSNSiteMicrothesaurusConfig>(
      `/sn/${snSiteId}/microthesaurus/config`,
      config,
    );
    return data;
  }
}
