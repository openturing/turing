import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import LoggingAemPage from "@/app/console/logging/instance/logging.aem.page";
import LoggingIndexingPage from "@/app/console/logging/instance/logging.indexing.page";
import LoggingServerPage from "@/app/console/logging/instance/logging.server.page";
import {
  IconDatabaseSearch,
  IconPlugConnected,
  IconServer,
  type Icon as TablerIcon,
} from "@tabler/icons-react";
import type { ComponentType } from "react";
import { useTranslation } from "react-i18next";

export type LoggingSource = "server" | "indexing" | "aem";

interface SourceMeta {
  icon: TablerIcon;
  /** Tailwind gradient for the hero leading chip. */
  gradient: string;
  titleKey: string;
  descKey: string;
  Page: ComponentType;
}

const SOURCES: Record<LoggingSource, SourceMeta> = {
  server: {
    icon: IconServer,
    gradient: "from-rose-500 to-pink-600",
    titleKey: "logging.turingServer",
    descKey: "logging.turingServerDesc",
    Page: LoggingServerPage,
  },
  indexing: {
    icon: IconDatabaseSearch,
    gradient: "from-amber-500 to-orange-600",
    titleKey: "logging.indexing",
    descKey: "logging.indexingDesc",
    Page: LoggingIndexingPage,
  },
  aem: {
    icon: IconPlugConnected,
    gradient: "from-indigo-500 to-violet-600",
    titleKey: "logging.aem",
    descKey: "logging.aemDesc",
    Page: LoggingAemPage,
  },
};

/**
 * Bento log-viewer shell. The three log sources (Turing server, indexing, AEM)
 * reuse the console viewer pages unchanged; those render only their grid +
 * refresh bar (no page title), so under the bento shell they'd have no header.
 * This wrapper adds the standard {@link BentoHero} (back-link to the logging
 * landing) above the reused viewer, matching every other Management surface.
 */
export default function BentoLoggingSourcePage({ source }: Readonly<{ source: LoggingSource }>) {
  const { t } = useTranslation();
  const meta = SOURCES[source];
  const Page = meta.Page;
  const Icon = meta.icon;

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_LOGGING}
        backLabel={t("home.features.logging.title")}
        leading={
          <span className={`grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br ${meta.gradient} text-white shadow-md`}>
            <Icon size={24} />
          </span>
        }
        title={t(meta.titleKey)}
        subtitle={t(meta.descKey)}
      />
      <Page />
    </>
  );
}
