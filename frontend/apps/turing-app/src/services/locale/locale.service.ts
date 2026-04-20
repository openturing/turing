import type { TurLocale } from "@/models/locale/locale.model";
import axios from "axios";

export class TurLocaleService {
  async query(): Promise<TurLocale[]> {
    const response = await axios.get<TurLocale[]>("/locale");
    return response.data;
  }
  async get(initials: string): Promise<TurLocale> {
    const response = await axios.get<TurLocale>(`/locale/${initials}`);
    return response.data;
  }
}
