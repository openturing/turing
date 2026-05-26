import axios from "axios";
import type { TurRoutine } from "@/models/genai/routine.model";

/**
 * Routine catalog client. Routines are deployment-wide (not agent-scoped):
 * a single "generate_proposal_pdf" routine serves every flow that needs
 * it. Backed by {@code /api/genai/routine} CRUD endpoints.
 *
 * @since 2026.3.1
 */
export class TurRoutineService {
  async query(): Promise<TurRoutine[]> {
    const response = await axios.get<TurRoutine[]>("/genai/routine");
    return response.data;
  }

  async get(id: string): Promise<TurRoutine> {
    const response = await axios.get<TurRoutine>(`/genai/routine/${id}`);
    return response.data;
  }

  async create(routine: TurRoutine): Promise<TurRoutine> {
    const response = await axios.post<TurRoutine>("/genai/routine", routine);
    return response.data;
  }

  async update(routine: TurRoutine): Promise<TurRoutine> {
    const response = await axios.put<TurRoutine>(
      `/genai/routine/${routine.id}`,
      routine,
    );
    return response.data;
  }

  async delete(routine: TurRoutine): Promise<boolean> {
    const response = await axios.delete(`/genai/routine/${routine.id}`);
    return response.status === 200;
  }
}
