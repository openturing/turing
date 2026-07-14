import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoHero, BentoTileGrid } from "@/components/bento";
import type { BentoTone } from "@/components/bento";
import { IconDatabaseSearch, IconPlugConnected, IconReceiptRupee, IconServer } from "@tabler/icons-react";
import type { Icon as TablerIcon } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento logging landing — T566. Lists the three log sources (Turing server,
 * indexing, AEM) as the frosted bento mosaic ({@link BentoTileGrid} + tiles)
 * whose cards deep-link to the Bento log sub-routes; the log viewers themselves
 * are the console pages reused unchanged under the Bento shell. Read-only
 * surface, so no "New" tile and no hero/save-bar CRUD.
 */
export default function BentoLoggingPage() {
  const { t } = useTranslation();

  const sources: {
    id: string;
    name: string;
    description: string;
    url: string;
    icon: TablerIcon;
    tone: BentoTone;
  }[] = [
    {
      id: "server",
      name: t("logging.turingServer"),
      description: t("logging.turingServerDesc"),
      url: `${ROUTES.BENTO_LOGGING}/server`,
      icon: IconServer,
      tone: "rose",
    },
    {
      id: "indexing",
      name: t("logging.indexing"),
      description: t("logging.indexingDesc"),
      url: `${ROUTES.BENTO_LOGGING}/indexing`,
      icon: IconDatabaseSearch,
      tone: "amber",
    },
    {
      id: "aem",
      name: t("logging.aem"),
      description: t("logging.aemDesc"),
      url: `${ROUTES.BENTO_LOGGING}/aem`,
      icon: IconPlugConnected,
      tone: "indigo",
    },
  ];

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_AREA_MANAGEMENT}
        backLabel={t("home.sections.management.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-rose-500 to-pink-600 text-white shadow-md">
            <IconReceiptRupee size={24} />
          </span>
        }
        title={t("home.features.logging.title")}
        subtitle={t("home.features.logging.description")}
      />
      <BentoTileGrid
        items={sources}
        tryAgainUrl={ROUTES.BENTO_LOGGING}
        hideNew
        itemKey={(s) => s.id}
        renderTile={(s) => (
          <BentoEntityTile
            to={s.url}
            defaultIcon={s.icon}
            tone={s.tone}
            title={s.name}
            description={s.description}
          />
        )}
        emptyTitle={t("home.features.logging.title")}
        emptyDescription={t("home.features.logging.description")}
      />
    </>
  );
}
