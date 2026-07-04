import { BentoHero } from "@/components/bento";
import SystemInfoPage from "@/app/console/system/system-info.page";
import { IconInfoCircle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento system info — T566. Runtime/environment dashboard (app · DB · memory ·
 * disk · AI spend + optional AI insights). Read-only, so per §XXXI.8 it sits
 * inside the shell: the reused {@link SystemInfoPage} renders under a
 * {@link BentoHero} passed as its `header` prop, which also keeps the surface
 * clear of the console SubPageHeader (SidebarProvider-free shell).
 */
export default function BentoSystemInfoPage() {
  const { t } = useTranslation();
  return (
    <SystemInfoPage
      header={
        <BentoHero
          eyebrow={t("home.sections.management.label")}
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
  );
}
