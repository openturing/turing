import axios from "axios";
import type {
  TurPersonaSource,
  TurPersonaSourceCreate,
} from "@/models/persona/persona-source.model.ts";

/**
 * CRUD client for a persona's evaluation "notebook" (Block AA / §XXVI.2),
 * nested under `/persona/{personaId}/source`. Mirrors the other admin services
 * — relies on the globally configured axios instance (baseURL `/api`, XSRF).
 */
export class TurPersonaSourceService {
  async query(personaId: string): Promise<TurPersonaSource[]> {
    const response = await axios.get<TurPersonaSource[]>(
      `/persona/${personaId}/source`
    );
    return response.data;
  }

  async createDocOrUrl(
    personaId: string,
    payload: TurPersonaSourceCreate
  ): Promise<TurPersonaSource> {
    const response = await axios.post<TurPersonaSource>(
      `/persona/${personaId}/source`,
      payload
    );
    return response.data;
  }

  async upload(
    personaId: string,
    file: File,
    sourceName?: string
  ): Promise<TurPersonaSource> {
    const form = new FormData();
    form.append("file", file);
    if (sourceName) {
      form.append("sourceName", sourceName);
    }
    const response = await axios.post<TurPersonaSource>(
      `/persona/${personaId}/source/upload`,
      form
    );
    return response.data;
  }

  async reextract(
    personaId: string,
    id: string
  ): Promise<TurPersonaSource> {
    const response = await axios.post<TurPersonaSource>(
      `/persona/${personaId}/source/${id}/extract`,
      {}
    );
    return response.data;
  }

  async delete(personaId: string, id: string): Promise<boolean> {
    const response = await axios.delete(`/persona/${personaId}/source/${id}`);
    return response.status === 200;
  }
}
