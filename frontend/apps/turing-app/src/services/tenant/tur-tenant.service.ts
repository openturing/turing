import axios from "axios";
import type {
  TurTenant,
  TurTenantSignupRequest,
} from "@/models/tenant/tenant.model";

/**
 * T278 / §XIV.6.1 — REST client for multi-tenant signup, "my tenants" and the
 * current-tenant switch.
 */
export class TurTenantService {
  async myTenants(): Promise<TurTenant[]> {
    const response = await axios.get<TurTenant[]>("/tenants/mine");
    return response.data;
  }

  async signup(request: TurTenantSignupRequest): Promise<TurTenant> {
    const response = await axios.post<TurTenant>("/signup", request);
    return response.data;
  }

  async switchTenant(slug: string): Promise<TurTenant> {
    const response = await axios.post<TurTenant>(
      `/tenants/${encodeURIComponent(slug)}/switch`,
    );
    return response.data;
  }

  // T279 — platform-admin console (requires ROLE_PLATFORM_ADMIN).
  async listAll(): Promise<TurTenant[]> {
    const response = await axios.get<TurTenant[]>("/platform/tenants");
    return response.data;
  }

  async suspend(id: string): Promise<TurTenant> {
    const response = await axios.post<TurTenant>(`/platform/tenants/${id}/suspend`);
    return response.data;
  }

  async activate(id: string): Promise<TurTenant> {
    const response = await axios.post<TurTenant>(`/platform/tenants/${id}/activate`);
    return response.data;
  }

  async impersonate(id: string): Promise<TurTenant> {
    const response = await axios.post<TurTenant>(`/platform/tenants/${id}/impersonate`);
    return response.data;
  }
}
