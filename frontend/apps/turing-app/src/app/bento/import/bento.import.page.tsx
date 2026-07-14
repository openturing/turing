import { BentoHero } from "@/components/bento";
import { ROUTES } from "@/app/routes.const";
import ExchangeImportPage from "@/app/console/exchange/exchange.import.page";
import { IconFileImport } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento import (content exchange) — T566. The ZIP upload / import surface is an
 * app-like drop zone, so per §XXXI.8 it sits under a {@link BentoHero} inside
 * the shell; the reused {@link ExchangeImportPage} keeps its drag-drop, conflict
 * prompt and progress logic unchanged.
 */
export default function BentoImportPage() {
  const { t } = useTranslation();
  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_AREA_MANAGEMENT}
        backLabel={t("home.sections.management.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-blue-500 to-indigo-600 text-white shadow-md">
            <IconFileImport size={24} />
          </span>
        }
        title={t("home.features.import.title")}
        subtitle={t("home.features.import.description")}
      />
      <ExchangeImportPage />
    </>
  );
}
