/**
 * T278 / §XIV.6.1 — multi-tenancy view models, mirroring the backend
 * `TurTenantResponse` (api/tenant) and the signup request.
 */
export interface TurTenant {
  id: string;
  slug: string;
  name: string;
  status: string;
  plan: string;
}

export interface TurTenantSignupRequest {
  slug: string;
  name?: string;
  username?: string;
}
