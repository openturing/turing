import { useMatchProjects } from "@/api/queries/persona-match.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { Button } from "@/components/ui/button";
import {
  IconCalendarRepeat,
  IconMasksTheater,
  IconTargetArrow,
  IconUsersGroup,
  IconUserCircle,
} from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

/**
 * Persona Match — project list (Block AT / §XLIII, T701).
 *
 * A GLOBAL surface at `/bento/persona/match`, sibling to Persona Dialogue: a
 * mosaic of reusable **analysis projects**, each grouping N contents × N
 * personas and producing an N×N fit matrix that re-runs on a schedule. This is
 * where content-fit lives now — pulled OUT of the per-persona validate notebook.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function BentoPersonaMatchListPage() {
  const { t } = useTranslation();
  const { data: projects, isError } = useMatchProjects();
  const error = isError
    ? t("common.connectionError", { resource: t("persona.match.connectionResource") })
    : null;

  return (
    <BentoListPage
      items={projects}
      error={error}
      tryAgainUrl={ROUTES.BENTO_PERSONA_MATCH}
      backTo={ROUTES.BENTO_PERSONA_INSTANCE}
      backLabel={t("persona.match.personas")}
      heroIcon={IconTargetArrow}
      tone="indigo"
      title={t("persona.match.title")}
      subtitle={t("persona.match.subtitle")}
      headerAction={
        <>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_INSTANCE}>
              <IconUserCircle size={16} />
              {t("persona.match.personas")}
            </Link>
          </Button>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_DIALOGUE}>
              <IconMasksTheater size={16} />
              {t("persona.match.dialogue")}
            </Link>
          </Button>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_RESEARCH}>
              <IconUsersGroup size={16} />
              {t("persona.match.research", { defaultValue: "Synthetic research" })}
            </Link>
          </Button>
        </>
      }
      newRoute={`${ROUTES.BENTO_PERSONA_MATCH}/new`}
      newLabel={t("persona.match.newLabel")}
      newSubtitle={t("persona.match.newSubtitle")}
      itemKey={(project) => project.id}
      emptyTitle={t("persona.match.emptyTitle")}
      emptyDescription={t("persona.match.emptyDescription")}
      listId="personaMatch"
      renderTile={(project, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_PERSONA_MATCH}/${project.id}`}
          emphasis={emphasis}
          defaultIcon={IconTargetArrow}
          tone="indigo"
          title={project.name}
          description={project.description ?? ""}
          hasStatus
          enabled={project.enabled ? 1 : 0}
          meta={
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                {t("persona.match.contentsCount", { count: project.sourceCount })}
              </span>
              <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                {t("persona.match.personasCount", { count: project.personaCount })}
              </span>
              <span className="inline-flex items-center gap-1 rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                <IconCalendarRepeat size={12} />
                {t(`persona.match.schedule.${project.schedule}`)}
              </span>
              {project.lastRunAt && (
                <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                  {t("persona.match.ranAt", {
                    date: new Date(project.lastRunAt).toLocaleDateString(),
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
