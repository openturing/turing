import axios from "axios";
import type { TurPageSite } from "@/models/page/page-site.model";

export class TurPageService {
  async query(): Promise<TurPageSite[]> {
    const response = await axios.get<TurPageSite[]>("/page");
    return response.data;
  }

  async upload(file: File, siteName: string): Promise<TurPageSite> {
    const formData = new FormData();
    formData.append("file", file);
    const response = await axios.post<TurPageSite>("/page", formData, {
      params: { siteName },
      headers: { "Content-Type": "multipart/form-data" },
    });
    return response.data;
  }

  async delete(siteName: string): Promise<void> {
    await axios.delete(`/page/${encodeURIComponent(siteName)}`);
  }

  getSiteUrl(siteName: string): string {
    return `/pages/${encodeURIComponent(siteName)}/`;
  }
}
