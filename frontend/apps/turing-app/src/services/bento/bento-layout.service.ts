import axios from "axios";
import type { BentoLayoutEntry, BentoLayoutResponse } from "@/components/bento/bento-layout";

/**
 * T575 / §XXXI.11 — client for the T574 customizable Bento layout API. The axios
 * baseURL already carries `/api`, so these paths hit `/api/v2/bento-layout/*`.
 */
export class TurBentoLayoutService {
  async resolve(listId: string): Promise<BentoLayoutResponse> {
    const response = await axios.get<BentoLayoutResponse>(`/v2/bento-layout/${listId}`);
    return response.data;
  }

  async saveUser(listId: string, entries: BentoLayoutEntry[]): Promise<BentoLayoutResponse> {
    const response = await axios.put<BentoLayoutResponse>(`/v2/bento-layout/${listId}`, entries);
    return response.data;
  }

  async saveGlobal(listId: string, entries: BentoLayoutEntry[]): Promise<BentoLayoutResponse> {
    const response = await axios.put<BentoLayoutResponse>(`/v2/bento-layout/${listId}/global`, entries);
    return response.data;
  }

  async reset(listId: string): Promise<BentoLayoutResponse> {
    const response = await axios.delete<BentoLayoutResponse>(`/v2/bento-layout/${listId}`);
    return response.data;
  }
}
