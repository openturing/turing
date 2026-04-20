import type { TurRole } from "./role";
import type { TurUser } from "./user";

export type TurGroup = {
  id: string;
  name: string;
  description: string;
  turUsers?: TurUser[];
  turRoles?: TurRole[];
};
