import { useMatchProjects } from "@/api/queries/persona-match.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { Button } from "@/components/ui/button";
import { SCHEDULE_LABEL } from "@/models/persona/persona-match.model";
import {
  IconCalendarRepeat,
  IconMasksTheater,
  IconTargetArrow,
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
    ? t("common.connectionError", { resource: "Persona Match" })
    : null;

  return (
    <BentoListPage
      items={projects}
      error={error}
      tryAgainUrl={ROUTES.BENTO_PERSONA_MATCH}
      eyebrow={
        <Link to={ROUTES.BENTO_PERSONA_INSTANCE} className="hover:text-foreground">
          Personas
        </Link>
      }
      heroIcon={IconTargetArrow}
      tone="indigo"
      title="Persona Match"
      subtitle="Agrupe conteúdos e personas em projetos e veja, numa matriz, o que cada conteúdo serve para cada persona — e vice-versa. A análise roda de tempos em tempos e reajusta os dois lados."
      headerAction={
        <>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_INSTANCE}>
              <IconUserCircle size={16} />
              Personas
            </Link>
          </Button>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_DIALOGUE}>
              <IconMasksTheater size={16} />
              Diálogo entre personas
            </Link>
          </Button>
        </>
      }
      newRoute={`${ROUTES.BENTO_PERSONA_MATCH}/new`}
      newLabel="Novo projeto de análise"
      newSubtitle="Conteúdos + personas → matriz de fit"
      itemKey={(project) => project.id}
      emptyTitle="Nenhum projeto de análise ainda"
      emptyDescription="Crie um grupo de conteúdos e personas para gerar o relatório N×N."
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
                {project.sourceCount} conteúdos
              </span>
              <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                {project.personaCount} personas
              </span>
              <span className="inline-flex items-center gap-1 rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                <IconCalendarRepeat size={12} />
                {SCHEDULE_LABEL[project.schedule]}
              </span>
              {project.lastRunAt && (
                <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
                  Rodou {new Date(project.lastRunAt).toLocaleDateString("pt-BR")}
                </span>
              )}
            </div>
          }
        />
      )}
    />
  );
}
