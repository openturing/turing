import { useResearchStudies } from "@/api/queries/persona-research.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { Button } from "@/components/ui/button";
import {
  IconCalendarRepeat,
  IconMasksTheater,
  IconMessageQuestion,
  IconStack2,
  IconTargetArrow,
  IconUserCircle,
  IconUsersGroup,
} from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

/**
 * Synthetic User Research — study list (Block AW / §XLVI.5, T730).
 *
 * The studio hub at `/bento/persona/research`, sibling to Persona Match &
 * Dialogue: a mosaic of reusable **research studies**, each an OCEAN-profiled
 * audience interviewed over a protocol → a synthesized insights report with
 * saturation scoring. This is the single rendering surface for the deliberately
 * headless Phase 2–4 backends.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function BentoPersonaResearchListPage() {
  const { t } = useTranslation();
  const { data: studies, isError } = useResearchStudies();
  const error = isError
    ? t("common.connectionError", { resource: t("persona.research.connectionResource") })
    : null;

  return (
    <BentoListPage
      items={studies}
      error={error}
      tryAgainUrl={ROUTES.BENTO_PERSONA_RESEARCH}
      backTo={ROUTES.BENTO_PERSONA_INSTANCE}
      backLabel={t("persona.research.personas")}
      heroIcon={IconUsersGroup}
      tone="violet"
      title={t("persona.research.title")}
      subtitle={t("persona.research.subtitle")}
      headerAction={
        <>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={`${ROUTES.BENTO_PERSONA_RESEARCH}/program`}>
              <IconStack2 size={16} />
              {t("persona.research.program.link")}
            </Link>
          </Button>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_INSTANCE}>
              <IconUserCircle size={16} />
              {t("persona.research.personas")}
            </Link>
          </Button>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_MATCH}>
              <IconTargetArrow size={16} />
              {t("persona.research.match")}
            </Link>
          </Button>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_DIALOGUE}>
              <IconMasksTheater size={16} />
              {t("persona.research.dialogue")}
            </Link>
          </Button>
        </>
      }
      newRoute={`${ROUTES.BENTO_PERSONA_RESEARCH}/new`}
      newLabel={t("persona.research.newLabel")}
      newSubtitle={t("persona.research.newSubtitle")}
      itemKey={(study) => study.id}
      emptyTitle={t("persona.research.emptyTitle")}
      emptyDescription={t("persona.research.emptyDescription")}
      listId="personaResearch"
      renderTile={(study, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_PERSONA_RESEARCH}/${study.id}`}
          emphasis={emphasis}
          defaultIcon={IconUsersGroup}
          tone="violet"
          title={study.name}
          description={study.goal ?? study.description ?? ""}
          hasStatus
          enabled={study.enabled ? 1 : 0}
          meta={
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="inline-flex items-center gap-1 rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                <IconMessageQuestion size={12} />
                {t(`persona.research.protocol.${study.protocol}`)}
              </span>
              <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                {t("persona.research.participantsCount", { count: study.personaCount })}
              </span>
              {study.interviewCount > 0 && (
                <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                  {t("persona.research.interviewsCount", { count: study.interviewCount })}
                </span>
              )}
              <span className="inline-flex items-center gap-1 rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                <IconCalendarRepeat size={12} />
                {t(`persona.research.schedule.${study.schedule}`)}
              </span>
              {study.lastRunAt && (
                <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                  {t("persona.research.ranAt", {
                    date: new Date(study.lastRunAt).toLocaleDateString(),
                  })}
                </span>
              )}
            </div>
          }
        />
      )}
    />
  );
}
