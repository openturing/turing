import {
  personaMatchService,
  useMatchProjects,
} from "@/api/queries/persona-match.queries";
import { usePersona } from "@/api/queries/persona.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { IconLoader2, IconTargetArrow } from "@tabler/icons-react";
import { useEffect, useRef } from "react";
import { useTranslation } from "react-i18next";
import { Navigate, useNavigate, useParams } from "react-router-dom";

/**
 * Persona content-fit validation — **folded into Persona Match** (Block AT /
 * §XLIII, T703). The per-persona validate notebook (Block AA, 1 persona × N
 * contents) is retired: this route now creates (or re-opens) a **single-persona
 * Match project** for the persona and redirects to the Persona Match studio,
 * where the same content-fit evaluator runs as an N×N project (here N = 1
 * persona). The "Analyze fit" launch from the persona detail (T583) still points
 * here, so the entry point is preserved while the canonical home becomes the
 * project. The old {@code PersonaSourcesSection}/{@code PersonaFitReportSection}
 * components remain, just no longer routed here.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function BentoPersonaValidatePage() {
  const { t } = useTranslation();
  const { personaId } = useParams<{ personaId: string }>();
  const navigate = useNavigate();
  const { data: persona } = usePersona(personaId);
  const { data: projects } = useMatchProjects();
  const acted = useRef(false);

  const markerName = persona
    ? t("persona.match.markerName", { name: persona.name })
    : null;

  useEffect(() => {
    if (acted.current || !personaId || !persona || !projects || !markerName) {
      return;
    }
    acted.current = true;

    // Re-open a matching single-persona project if one already exists.
    const existing = projects.find(
      (p) => p.name === markerName && p.personaCount === 1,
    );
    if (existing) {
      navigate(`${ROUTES.BENTO_PERSONA_MATCH}/${existing.id}`, { replace: true });
      return;
    }

    // Otherwise spin one up, attach the persona, and open it.
    (async () => {
      try {
        const project = await personaMatchService.create({
          name: markerName,
          description: t("persona.match.singlePersonaDesc", { name: persona.name }),
          enabled: true,
          schedule: "MANUAL",
        });
        await personaMatchService.setPersonas(project.id, [personaId]);
        navigate(`${ROUTES.BENTO_PERSONA_MATCH}/${project.id}`, { replace: true });
      } catch {
        acted.current = false; // allow a retry on transient failure
        navigate(ROUTES.BENTO_PERSONA_MATCH, { replace: true });
      }
    })();
  }, [personaId, persona, projects, markerName, navigate]);

  if (!personaId) {
    return <Navigate to={ROUTES.BENTO_PERSONA_INSTANCE} replace />;
  }

  return (
    <BentoHero
      leading={
        <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-indigo-600 to-fuchsia-600 text-white shadow-md">
          <IconTargetArrow size={24} />
        </span>
      }
      title={t("persona.match.validateOpening")}
      subtitle={
        <span className="inline-flex items-center gap-2">
          <IconLoader2 className="size-4 animate-spin" />
          {t("persona.match.validatePreparing", {
            name: persona?.name ?? t("persona.match.validateThisPersona"),
          })}
        </span>
      }
    />
  );
}
