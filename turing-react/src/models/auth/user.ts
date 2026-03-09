export type TurUser = {
  username: string;
  firstName: string;
  lastName: string;
  admin: boolean;
  email: string;
  password?: string;
  hasAvatar?: boolean;
  avatarUrl?: string;
};
