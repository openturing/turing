import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { GridList } from "@/components/grid.list";
import { IconReceiptRupee } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento logging landing — T566. Lists the three log sources (Turing server,
 * indexing, AEM) as a frosted {@link GridList} whose cards deep-link to the
 * Bento log sub-routes; the log viewers themselves are the console pages reused
 * unchanged under the Bento shell. Read-only surface, so no hero/save-bar CRUD.
 */
export default function BentoLoggingPage() {
  const { t } = useTranslation();

  const gridItemList = [
    {
      id: "1",
      name: t("logging.turingServer"),
      description: t("logging.turingServerDesc"),
      url: `${ROUTES.BENTO_LOGGING}/server`,
    },
    {
      id: "2",
      name: t("logging.indexing"),
      description: t("logging.indexingDesc"),
      url: `${ROUTES.BENTO_LOGGING}/indexing`,
    },
    {
      id: "3",
      name: t("logging.aem"),
      description: t("logging.aemDesc"),
      url: `${ROUTES.BENTO_LOGGING}/aem`,
    },
  ];

  return (
    <>
      <BentoHero
        eyebrow={t("home.sections.management.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-rose-500 to-pink-600 text-white shadow-md">
            <IconReceiptRupee size={24} />
          </span>
        }
        title={t("home.features.logging.title")}
        subtitle={t("home.features.logging.description")}
      />
      <GridList gridItemList={gridItemList} />
    </>
  );
}
