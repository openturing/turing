import type { TurPrivilege } from "@/models/auth/role";
import axios from "axios";

export class TurPrivilegeService {
  async query(): Promise<TurPrivilege[]> {
    const response = await axios.get<TurPrivilege[]>("/v2/privilege");
    return response.data;
  }
}
