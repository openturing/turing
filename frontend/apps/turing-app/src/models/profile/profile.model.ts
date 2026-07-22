/** A generic, system-wide (global) profile, managed under "Management". */
export type TurProfile = {
  id: string;
  name: string;
  description?: string;
  default: boolean;
};
