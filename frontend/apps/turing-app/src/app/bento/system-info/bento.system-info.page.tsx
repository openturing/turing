import { BentoHero, bentoChipClass } from "@/components/bento";
import { ROUTES } from "@/app/routes.const";
import SystemInfoPage from "@/app/console/system/system-info.page";
import { IconInfoCircle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento system info — T566. Runtime/environment dashboard (app · DB · memory ·
 * disk · AI spend + optional AI insights). Read-only, so per §XXXI.8 it sits
 * inside the shell: the reused {@link SystemInfoPage} renders under a
 * {@link BentoHero} passed as its `header` prop, and its `SectionCard` blocks
 * render as frosted {@link BentoFormSection} cards so the body looks native,
 * not like flat console cards under an airy hero.
 *
 * That used to be declared here with `chrome="bento"`. The design system removed
 * the switch once every consumer had cut over (VDS146), and bento had been the
 * default since VDS145.
 */
export default function BentoSystemInfoPage() {
  const { t } = useTranslation();
  return (
    <SystemInfoPage
      header={
        <BentoHero
          backTo={ROUTES.BENTO_AREA_MANAGEMENT}
          backLabel={t("home.sections.management.label")}
          leading={
            <span className={`grid h-12 w-12 place-items-center rounded-2xl ${bentoChipClass("slate")} text-white shadow-md`}>
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
