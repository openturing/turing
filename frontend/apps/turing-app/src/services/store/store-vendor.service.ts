import axios from "axios";
import type { TurStoreVendor } from "@/models/store/store-vendor.model.ts";

export class TurStoreVendorService {
  async query(): Promise<TurStoreVendor[]> {
    const response = await axios.get<TurStoreVendor[]>("/store/vendor");
    return response.data;
  }
}
