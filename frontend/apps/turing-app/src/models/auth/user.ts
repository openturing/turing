import type { TurGroup } from "./group";

export type TurUser = {
  username: string;
  firstName: string;
  lastName: string;
  admin: boolean;
  email: string;
  password?: string;
  hasAvatar?: boolean;
  avatarUrl?: string;
  realm?: string;
  turGroups?: TurGroup[];
  privileges?: string[];
};
