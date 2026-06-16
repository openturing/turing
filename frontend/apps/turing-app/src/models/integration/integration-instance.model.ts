import type { TurTokenInstance } from "@/models/token/token-instance.model";

export type TurIntegrationInstance = {
  id: string;
  title: string;
  description: string;
  endpoint: string;
  enabled: number;
  icon?: string | null;
  apiToken?: TurTokenInstance | null;
  /** T372 — null for the shared GLOBAL BYO-infra pool (read-only when tenancy is on). */
  tenantId?: string | null;
};
