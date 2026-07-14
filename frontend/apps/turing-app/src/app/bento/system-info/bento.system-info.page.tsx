import { BentoHero } from "@/components/bento";
import { ROUTES } from "@/app/routes.const";
import { SectionCardChromeProvider } from "@/components/ui/section-card";
import SystemInfoPage from "@/app/console/system/system-info.page";
import { IconInfoCircle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento system info — T566. Runtime/environment dashboard (app · DB · memory ·
 * disk · AI spend + optional AI insights). Read-only, so per §XXXI.8 it sits
 * inside the shell: the reused {@link SystemInfoPage} renders under a
 * {@link BentoHero} passed as its `header` prop, which keeps the surface clear
 * of the console SubPageHeader (SidebarProvider-free shell). Wrapping it in a
 * `bento` {@link SectionCardChromeProvider} also turns its `SectionCard` blocks
 * into frosted {@link BentoFormSection} cards so the body looks native, not
 * flat console cards under an airy hero.
 */
export default function BentoSystemInfoPage() {
  const { t } = useTranslation();
  return (
    <SectionCardChromeProvider chrome="bento">
      <SystemInfoPage
        header={
          <BentoHero
            backTo={ROUTES.BENTO_AREA_MANAGEMENT}
            backLabel={t("home.sections.management.label")}
            leading={
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-slate-500 to-slate-700 text-white shadow-md">
                <IconInfoCircle size={24} />
              </span>
            }
            title={t("systemInfo.title")}
            subtitle={t("systemInfo.description")}
          />
        }
      />
    </SectionCardChromeProvider>
  );
}
