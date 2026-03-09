export type TurUser = {
  id: number;
  username: string;
  password: string;
  firstName: string;
  lastName: string;
  authdata: string;
  admin: boolean;
  email: string;
  hasAvatar?: boolean;
  avatarUrl?: string;
};
