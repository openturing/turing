import { TurAdmUser } from "./adm-user.model";

export interface TurAdmGroup {
  description: string;
  id: string;
  name: string;
  turUsers: TurAdmUser[];
}
