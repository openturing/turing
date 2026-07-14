import { useLlmInstances } from "@/api/queries/llm-instance.queries";
import {
  BENTO_SPAN_FEATURED,
  BENTO_SPAN_SQUARE,
  BentoHero,
  BentoTile,
  bentoNavTarget,
  bentoSectionByAreaRoute,
  useVisibleBentoSections,
} from "@/components/bento";
import { useMemo } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ROUTES } from "@/app/routes.const";

/**
 * Generic Bento **area hub** (T573). Each of the three nav-rail sections
 * (Generative AI · Enterprise Search · Management) routes here; the page reads
 * the section from its own `areaRoute`, then renders every surface of that
 * section as a bento mosaic. Migrated surfaces navigate inside the shell;
 * unmigrated ones link out to the console via `bentoNavTarget`.
 *
 * This is the "spoke" of the hub-and-spoke IA that lets the rail stay at a
 * fixed handful of icons instead of listing all ~22 leaves.
 */
export default function BentoAreaPage() {
  const { t } = useTranslation();
  const { pathname } = useLocation();

  // Route is exact (e.g. `/bento/area/generative-ai`); tolerate a trailing slash.
  const areaRoute = pathname.replace(/\/$/, "");
  const section = bentoSectionByAreaRoute(areaRoute);
  const groups = useVisibleBentoSections();
  const group = section ? groups.find((g) => g.section.id === section.id) : undefined;

  // Hide LLM-gated surfaces (e.g. Chat) until an LLM is enabled — same rule the
  // home overview applies, so the hub and the overview stay consistent.
  const { data: llmInstances } = useLlmInstances();
  const hasEnabledLlm = useMemo(
    () => (llmInstances ?? []).some((i) => i.enabled === 1),
    [llmInstances],
  );
  const items = useMemo(
    () => (group?.items ?? []).filter((i) => !i.requiresLlm || hasEnabledLlm),
    [group, hasEnabledLlm],
  );

  // Unknown hub, or the user can't see anything in it → back to home rather
  // than render an empty shell.
  if (!section || !group) {
    return <Navigate to={ROUTES.BENTO_HOME} replace />;
  }

  const label = t(section.labelKey ?? "");
  const description = section.descriptionKey ? t(section.descriptionKey) : undefined;
  const SectionIcon = section.icon;

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_HOME}
        backLabel={t("home.title")}
        leading={SectionIcon ? <SectionIcon size={40} /> : undefined}
        title={label}
        subtitle={description}
      />

      {/*
       * The hero above already carries the section title + description, so the
       * mosaic renders as a bare `bento-grid` (no repeated `BentoSection`
       * header). Keeping the `bento-grid` class preserves the stagger reveal.
       */}
      <div className="bento-grid grid auto-rows-[minmax(140px,auto)] grid-cols-2 gap-4 md:grid-cols-4 md:gap-5 lg:grid-cols-6">
        {items.map((item) => (
          <BentoTile
            key={item.id}
            to={bentoNavTarget(item)}
            icon={item.icon}
            tone={item.tone}
            eyebrow={label}
            title={t(item.titleKey)}
            span={item.span ?? BENTO_SPAN_SQUARE}
            featured={item.span === BENTO_SPAN_FEATURED}
          >
            <p className="text-sm text-muted-foreground">{t(item.descriptionKey)}</p>
          </BentoTile>
        ))}
      </div>
    </>
  );
}
