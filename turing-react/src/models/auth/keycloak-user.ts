export type TurKeycloakUser = {
  id: string;
  username: string;
  firstName?: string;
  lastName?: string;
  email?: string;
  enabled?: boolean;
  emailVerified?: boolean;
  createdTimestamp?: number;
  groups?: string[];
  realmRoles?: string[];
};
