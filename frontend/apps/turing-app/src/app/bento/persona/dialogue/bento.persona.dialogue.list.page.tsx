import { useDialogueProjects } from "@/api/queries/persona-dialogue.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { Button } from "@/components/ui/button";
import {
  IconMessages,
  IconMasksTheater,
  IconRepeat,
  IconTargetArrow,
  IconUsersGroup,
  IconUserCircle,
} from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

/**
 * Persona Dialogue — project list (Block AU / §XLIV, T706).
 *
 * A GLOBAL surface at `/bento/persona/dialogue`, sibling to Persona Match: a
 * mosaic of saved **dialogue projects**, each a topic + ordered speaker roster
 * that streams a turn-by-turn conversation and persists its last transcript.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function BentoPersonaDialogueListPage() {
  const { t } = useTranslation();
  const { data: projects, isError } = useDialogueProjects();
  const error = isError
    ? t("common.connectionError", { resource: t("persona.dialogueProject.connectionResource") })
    : null;

  return (
    <BentoListPage
      items={projects}
      error={error}
      tryAgainUrl={ROUTES.BENTO_PERSONA_DIALOGUE}
      backTo={ROUTES.BENTO_PERSONA_INSTANCE}
      backLabel={t("persona.dialogueProject.personas")}
      heroIcon={IconMasksTheater}
      tone="rose"
      title={t("persona.dialogueProject.title")}
      subtitle={t("persona.dialogueProject.subtitle")}
      headerAction={
        <>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_INSTANCE}>
              <IconUserCircle size={16} />
              {t("persona.dialogueProject.personas")}
            </Link>
          </Button>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_MATCH}>
              <IconTargetArrow size={16} />
              {t("persona.dialogueProject.match")}
            </Link>
          </Button>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_RESEARCH}>
              <IconUsersGroup size={16} />
              {t("persona.dialogueProject.research", { defaultValue: "Synthetic research" })}
            </Link>
          </Button>
        </>
      }
      newRoute={`${ROUTES.BENTO_PERSONA_DIALOGUE}/new`}
      newLabel={t("persona.dialogueProject.newLabel")}
      newSubtitle={t("persona.dialogueProject.newSubtitle")}
      itemKey={(project) => project.id}
      emptyTitle={t("persona.dialogueProject.emptyTitle")}
      emptyDescription={t("persona.dialogueProject.emptyDescription")}
      listId="personaDialogue"
      renderTile={(project, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_PERSONA_DIALOGUE}/${project.id}`}
          emphasis={emphasis}
          defaultIcon={IconMasksTheater}
          tone="rose"
          title={project.name}
          description={project.description ?? project.topic ?? ""}
          meta={
            <div className="flex flex-wrap items-center gap-1.5">
              <span className="inline-flex items-center gap-1 rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                <IconUserCircle size={12} />
                {t("persona.dialogueProject.speakersCount", { count: project.speakerCount })}
              </span>
              <span className="inline-flex items-center gap-1 rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                <IconRepeat size={12} />
                {t("persona.dialogueProject.turnsCount", { count: project.turns })}
              </span>
              {project.lastRunAt && (
                <span className="inline-flex items-center gap-1 rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                  <IconMessages size={12} />
                  {t("persona.dialogueProject.ranAt", {
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
