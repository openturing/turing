import axios from "axios";
import type {
  TurKnowledgeBase,
  TurMicrothesaurus,
  TurThesaurusSeed,
  TurThesaurusTerm,
} from "@/models/kb/thesaurus.model";

// Block AQ (§XL) — Thesaurus admin REST client. Backend routes are under
// `/api/kb` (internal package name); the user-facing surface is "Thesaurus".
export class TurThesaurusService {
  // ---- Knowledge Base (library) ----
  async listKnowledgeBases(): Promise<TurKnowledgeBase[]> {
    const { data } = await axios.get<TurKnowledgeBase[]>("/api/kb");
    return data;
  }
  async getKnowledgeBase(id: string): Promise<TurKnowledgeBase> {
    const { data } = await axios.get<TurKnowledgeBase>(`/api/kb/${id}`);
    return data;
  }
  async createKnowledgeBase(kb: TurKnowledgeBase): Promise<TurKnowledgeBase> {
    const { data } = await axios.post<TurKnowledgeBase>("/api/kb", kb);
    return data;
  }
  async updateKnowledgeBase(kb: TurKnowledgeBase): Promise<TurKnowledgeBase> {
    const { data } = await axios.put<TurKnowledgeBase>(`/api/kb/${kb.id}`, kb);
    return data;
  }
  async deleteKnowledgeBase(kb: TurKnowledgeBase): Promise<boolean> {
    const res = await axios.delete(`/api/kb/${kb.id}`);
    return res.status === 200;
  }

  // ---- Microthesaurus (one tree = language + domain) ----
  async listMicrothesauri(knowledgeBaseId: string): Promise<TurMicrothesaurus[]> {
    const { data } = await axios.get<TurMicrothesaurus[]>(
      `/api/kb/${knowledgeBaseId}/microthesaurus`,
    );
    return data;
  }
  async getMicrothesaurus(
    knowledgeBaseId: string,
    id: string,
  ): Promise<TurMicrothesaurus> {
    const { data } = await axios.get<TurMicrothesaurus>(
      `/api/kb/${knowledgeBaseId}/microthesaurus/${id}`,
    );
    return data;
  }
  async createMicrothesaurus(
    knowledgeBaseId: string,
    m: TurMicrothesaurus,
  ): Promise<TurMicrothesaurus> {
    const { data } = await axios.post<TurMicrothesaurus>(
      `/api/kb/${knowledgeBaseId}/microthesaurus`,
      m,
    );
    return data;
  }
  async updateMicrothesaurus(
    knowledgeBaseId: string,
    m: TurMicrothesaurus,
  ): Promise<TurMicrothesaurus> {
    const { data } = await axios.put<TurMicrothesaurus>(
      `/api/kb/${knowledgeBaseId}/microthesaurus/${m.id}`,
      m,
    );
    return data;
  }
  async deleteMicrothesaurus(
    knowledgeBaseId: string,
    id: string,
  ): Promise<boolean> {
    const res = await axios.delete(
      `/api/kb/${knowledgeBaseId}/microthesaurus/${id}`,
    );
    return res.status === 200;
  }

  // ---- Terms ----
  async listTerms(microthesaurusId: string): Promise<TurThesaurusTerm[]> {
    const { data } = await axios.get<TurThesaurusTerm[]>(
      `/api/kb/microthesaurus/${microthesaurusId}/term`,
    );
    return data;
  }
  async createTerm(
    microthesaurusId: string,
    term: TurThesaurusTerm,
  ): Promise<TurThesaurusTerm> {
    const { data } = await axios.post<TurThesaurusTerm>(
      `/api/kb/microthesaurus/${microthesaurusId}/term`,
      term,
    );
    return data;
  }
  async updateTerm(
    microthesaurusId: string,
    term: TurThesaurusTerm,
  ): Promise<TurThesaurusTerm> {
    const { data } = await axios.put<TurThesaurusTerm>(
      `/api/kb/microthesaurus/${microthesaurusId}/term/${term.id}`,
      term,
    );
    return data;
  }
  async deleteTerm(
    microthesaurusId: string,
    id: string,
  ): Promise<boolean> {
    const res = await axios.delete(
      `/api/kb/microthesaurus/${microthesaurusId}/term/${id}`,
    );
    return res.status === 200;
  }

  // ---- Seed library ----
  async listSeeds(): Promise<TurThesaurusSeed[]> {
    const { data } = await axios.get<TurThesaurusSeed[]>("/api/kb/seeds");
    return data;
  }
  async importSeed(
    knowledgeBaseId: string,
    seedId: string,
  ): Promise<TurMicrothesaurus> {
    const { data } = await axios.post<TurMicrothesaurus>(
      `/api/kb/${knowledgeBaseId}/seeds/${seedId}`,
      {},
    );
    return data;
  }
}
