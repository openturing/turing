import { BentoHero } from "@/components/bento";
import { ROUTES } from "@/app/routes.const";
import TenantSettingsPage from "@/app/console/tenant/tenant-settings.page";
import { IconBuildingCommunity } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento tenant settings — T568. The org switcher + self-service signup is a
 * plain Card-based surface with no console chrome, so it "sits inside the shell"
 * per §XXXI.9: a {@link BentoHero} over the reused `TenantSettingsPage`.
 */
export default function BentoTenantSettingsPage() {
  const { t } = useTranslation();
  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_AREA_MANAGEMENT}
        backLabel={t("home.sections.management.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-slate-500 to-slate-700 text-white shadow-md">
            <IconBuildingCommunity size={24} />
          </span>
        }
        title={t("nav.organizations", { defaultValue: "Organizations" })}
        subtitle={t("nav.organizationsDesc", { defaultValue: "Switch the active organization or create a new one." })}
      />
      <TenantSettingsPage />
    </>
  );
}
