import { lazy } from "react";
import { Route } from "react-router-dom";
import { ROUTES } from "../routes.const";

// T278 — tenant context (org switcher / signup / settings).
const TenantSettingsPage = lazy(
  () => import("../console/tenant/tenant-settings.page"),
);
// T279 — platform-admin tenant console.
const PlatformTenantAdminPage = lazy(
  () => import("../console/tenant/platform-tenant-admin.page"),
);

export const TenantRoutes = (
  <>
    <Route path={ROUTES.TENANT_SETTINGS} element={<TenantSettingsPage />} />
    <Route path={ROUTES.TENANT_ADMIN} element={<PlatformTenantAdminPage />} />
  </>
);
