import axios from "axios";
import type {
  AiAuthoringRequest,
  AiAuthoringResponse,
} from "@/models/ai-authoring/ai-authoring.model";
import type {
  PersonaGeneration,
  TurPersona,
} from "@/models/persona/persona.model.ts";

export class TurPersonaService {
  async query(): Promise<TurPersona[]> {
    const response = await axios.get<TurPersona[]>("/persona");
    return response.data;
  }
  async get(id: string): Promise<TurPersona> {
    const response = await axios.get<TurPersona>(`/persona/${id}`);
    return response.data;
  }
  async create(persona: TurPersona): Promise<TurPersona> {
    const response = await axios.post<TurPersona>("/persona", persona);
    return response.data;
  }
  async update(persona: TurPersona): Promise<TurPersona> {
    const response = await axios.put<TurPersona>(
      `/persona/${persona.id.toString()}`,
      persona
    );
    return response.data;
  }
  async delete(persona: TurPersona): Promise<boolean> {
    const response = await axios.delete<TurPersona>(
      `/persona/${persona.id.toString()}`
    );
    return response.status == 200;
  }
  /**
   * AI Authoring chat — drafts (or revises) a persona based on a natural
   * language brief. The chat sees the form's snapshot each turn and returns
   * the FULL updated state so the form re-populates wholesale.
   *
   * @since 2026.2.7
   */
  async aiChat(
    request: AiAuthoringRequest<PersonaGeneration>,
  ): Promise<AiAuthoringResponse<PersonaGeneration>> {
    const response = await axios.post<AiAuthoringResponse<PersonaGeneration>>(
      "/persona/chat",
      request,
    );
    return response.data;
  }
}
