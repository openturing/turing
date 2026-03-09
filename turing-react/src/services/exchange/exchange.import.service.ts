import axios from "axios";

export class TurExchangeImportService {
  async importFile(
    file: File,
    onProgress?: (progress: number) => void,
  ): Promise<void> {
    const formData = new FormData();
    formData.append("file", file);

    await axios.post("/import", formData, {
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total && onProgress) {
          const percent = Math.round(
            (progressEvent.loaded * 100) / progressEvent.total,
          );
          onProgress(percent);
        }
      },
    });
  }
}
