import { TurAdmGroup } from "./adm-group.model";

export interface TurAdmUser {
  confirmEmail: string;
  email: string;
  firstName: string;
  gravatar: number;
  lastLogin: Date;
  lastName: string;
  loginTimes: number;
  password: string;
  realm: string;
  recoverPassword: string;
  username: string;
  turGroups: TurAdmGroup[];
}
