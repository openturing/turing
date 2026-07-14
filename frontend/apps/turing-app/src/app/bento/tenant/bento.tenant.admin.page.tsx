import { BentoHero } from "@/components/bento";
import { ROUTES } from "@/app/routes.const";
import PlatformTenantAdminPage from "@/app/console/tenant/platform-tenant-admin.page";
import { IconBuildingSkyscraper } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento platform tenant admin — T568. The all-tenants table (suspend /
 * reactivate / impersonate, ROLE_PLATFORM_ADMIN-gated on the backend) has no
 * console chrome, so it "sits inside the shell" per §XXXI.9: a {@link BentoHero}
 * over the reused `PlatformTenantAdminPage`.
 */
export default function BentoTenantAdminPage() {
  const { t } = useTranslation();
  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_AREA_MANAGEMENT}
        backLabel={t("home.sections.management.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-slate-500 to-slate-700 text-white shadow-md">
            <IconBuildingSkyscraper size={24} />
          </span>
        }
        title={t("nav.tenantAdmin", { defaultValue: "Tenant administration" })}
        subtitle={t("nav.tenantAdminDesc", { defaultValue: "Platform-wide tenant management: suspend, reactivate, or impersonate." })}
      />
      <PlatformTenantAdminPage />
    </>
  );
}
