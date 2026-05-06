import axios from "axios";
import type { TurPersona } from "@/models/persona/persona.model.ts";

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
}
