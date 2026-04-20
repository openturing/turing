import type { TurTokenInstance } from "@/models/token/token-instance.model";

export type TurIntegrationInstance = {
  id: string;
  title: string;
  description: string;
  endpoint: string;
  enabled: number;
  icon?: string | null;
  apiToken?: TurTokenInstance | null;
};
