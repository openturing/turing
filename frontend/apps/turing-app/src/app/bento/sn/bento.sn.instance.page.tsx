import { useDeleteSnSite, useSnSite, useUpdateSnSite } from "@/api/queries/sn-site.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityShell } from "@/components/bento";
import { LoadProvider } from "@/components/loading-provider";
import type { TurSNSite } from "@/models/sn/sn-site.model.ts";
import {
  IconArrowsShuffle,
  IconSitemap,
  IconArrowsSort,
  IconAlignBoxCenterStretch,
  IconChartHistogram,
  IconCompass,
  IconCpu2,
  IconDashboard,
  IconDownload,
  IconFilter,
  IconGavel,
  IconGitMerge,
  IconLanguage,
  IconNumber123,
  IconScale,
  IconSparkles,
  IconSpeakerphone,
  type Icon as TablerIcon,
} from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router-dom";
import { BENTO_SN_FORM_ID, BentoSNInstanceForm } from "./bento.sn.instance.form";
import { BentoSnExportDialog } from "./bento.sn.export.dialog";
import { SnLaunchBar } from "./sn.launch.bar";

/**
 * SN uses `name` (not `title`) and has no `enabled` status flag, so the shared
 * shell's identity is aliased at the page boundary (title↔name). It has an
 * `icon`, so the hero icon picker stays on.
 */
type SNView = TurSNSite & { title: string };

const toView = (s: TurSNSite): SNView => ({ ...s, title: s.name ?? "" });

const fromView = (view: SNView): TurSNSite => {
  const { title, ...rest } = view;
  return { ...rest, name: title ?? rest.name };
};

/**
 * The advanced SN config sections. T557 migrated the **instance** (identity +
 * engine/store bindings + search template), T558 fields/coverage, and T576 the
 * remaining deep sections — so every section is now a Bento sub-route reached
 * via this panel.
 */
interface SectionLink {
  labelKey: string;
  icon: TablerIcon;
  path: string;
}

const SN_SECTIONS: readonly SectionLink[] = [
  { labelKey: "sn.insights.title", icon: IconSparkles, path: "insights" },
  { labelKey: "sn.fields.title", icon: IconAlignBoxCenterStretch, path: "field" },
  { labelKey: "sn.fieldCoverage.title", icon: IconChartHistogram, path: "field-coverage" },
  { labelKey: "sn.multiLanguage.title", icon: IconLanguage, path: "locale" },
  { labelKey: "sn.behavior.title", icon: IconScale, path: "behavior" },
  { labelKey: "sn.facets.title", icon: IconFilter, path: "facet" },
  { labelKey: "sn.customSort.title", icon: IconArrowsSort, path: "custom-sort" },
  { labelKey: "sn.searchRule.title", icon: IconGavel, path: "search-rule" },
  { labelKey: "sn.genai.title", icon: IconCpu2, path: "ai" },
  { labelKey: "sn.resultRanking.title", icon: IconNumber123, path: "result-ranking" },
  { labelKey: "sn.mergeProviders.title", icon: IconGitMerge, path: "merge-providers" },
  { labelKey: "sn.spotlight.title", icon: IconSpeakerphone, path: "spotlight" },
  { labelKey: "sn.synonym.title", icon: IconArrowsShuffle, path: "synonym" },
  { labelKey: "thesaurus.perSiteTitle", icon: IconSitemap, path: "microthesaurus" },
  { labelKey: "sn.topSearchTerms.title", icon: IconDashboard, path: "top-terms" },
];

function SNSectionsPanel({ siteId }: Readonly<{ siteId: string }>) {
  const { t } = useTranslation();
  return (
    <section className="mt-2">
      <h2 className="mb-3 flex items-center gap-2 text-sm font-medium text-muted-foreground">
        <IconSparkles size={16} className="text-emerald-500" />
        {t("sn.settings.title")}
      </h2>
      <div className="grid grid-cols-2 gap-3 md:grid-cols-3 lg:grid-cols-4">
        {SN_SECTIONS.map(({ labelKey, icon: Icon, path }) => (
          <Link
            key={path}
            to={`${ROUTES.BENTO_SN_INSTANCE}/${siteId}/${path}`}
            className="bento-tile bento-tile-clickable bento-glass flex items-center gap-3 rounded-2xl p-4"
          >
            <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-sm">
              <Icon size={18} />
            </span>
            <span className="text-sm font-medium">{t(labelKey)}</span>
          </Link>
        ))}
      </div>
    </section>
  );
}

export default function BentoSNInstancePage() {
  const { id } = useParams() as { id: string };
  const { t } = useTranslation();
  const isNew = id === "new";

  const { data: site, isError } = useSnSite(isNew ? undefined : id);
  const error = isError ? t("common.connectionError", { resource: t("sn.title") }) : null;

  const updateMutation = useUpdateSnSite();
  const deleteMutation = useDeleteSnSite();
  const [exportOpen, setExportOpen] = useState(false);

  function shell(entity: TurSNSite, headlineFallback: string) {
    return (
      <BentoEntityShell<SNView>
        entity={toView(entity)}
        isNew={isNew}
        headlineFallback={headlineFallback}
        eyebrow={t("sn.title")}
        listRoute={ROUTES.BENTO_SN_INSTANCE}
        icon={IconCompass}
        tone="emerald"
        formId={BENTO_SN_FORM_ID}
        feature={t("sn.title")}
        onUpdate={isNew ? undefined : (next) => updateMutation.mutateAsync(fromView(next))}
        onDelete={isNew ? undefined : () => deleteMutation.mutateAsync(entity)}
        extraActions={
          isNew
            ? undefined
            : [{ label: t("sn.export.title"), icon: IconDownload, onSelect: () => setExportOpen(true) }]
        }
      >
        {({ staged, onStateChange }) => (
          <>
            <BentoSNInstanceForm value={entity} isNew={isNew} staged={staged} onStateChange={onStateChange} />
            {!isNew && entity.id && (
              <>
                <SnLaunchBar site={entity} />
                <SNSectionsPanel siteId={entity.id} />
                <BentoSnExportDialog snSite={entity} open={exportOpen} onOpenChange={setExportOpen} />
              </>
            )}
          </>
        )}
      </BentoEntityShell>
    );
  }

  if (isNew) {
    return shell({} as TurSNSite, t("sn.newSite"));
  }

  return (
    <LoadProvider checkIsNotUndefined={site} error={error} tryAgainUrl={`${ROUTES.BENTO_SN_INSTANCE}/${id}`}>
      {site && shell(site, site.name)}
    </LoadProvider>
  );
}
