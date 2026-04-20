import type { TurSNFieldType } from "@/models/sn/sn-field-type.model";
import axios from "axios";

export class TurSNFieldTypeService {
  async query(): Promise<TurSNFieldType[]> {
    const response = await axios.get<TurSNFieldType[]>("/sn/field/ext/types");
    return response.data;
  }
}
