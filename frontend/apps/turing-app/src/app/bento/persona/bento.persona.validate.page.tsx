import { usePersona } from "@/api/queries/persona.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { PersonaFitReportSection } from "@/components/persona/persona-fit-report.section";
import { PersonaSourcesSection } from "@/components/persona/persona-sources.section";
import { SectionCardChromeProvider } from "@/components/ui/section-card";
import { IconFileSearch } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Link, Navigate, useParams } from "react-router-dom";

/**
 * Bento persona content-fit validation — Block AI / §XXXII.7 (T584). Promotes
 * the fit report from a buried editor tab into a first-class launch surface
 * reached from the Persona Launchpad (T583): a full-page "add sources → score"
 * flow (mirroring how "Open chat" is its own shareable page, not a tab). It
 * composes Block AA's notebook (T465, {@link PersonaSourcesSection}) and the
 * grounded content-fit report (T467/T468, {@link PersonaFitReportSection})
 * verbatim — both self-contained on the persona id. The shared `SectionCard`
 * bodies render as frosted bento cards via the `bento`
 * {@link SectionCardChromeProvider}. Only audience personas reach this (the
 * launchpad gates the action), so a missing id bounces to the persona list.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function BentoPersonaValidatePage() {
  const { t } = useTranslation();
  const { personaId } = useParams<{ personaId: string }>();
  const { data: persona } = usePersona(personaId);

  if (!personaId) {
    return <Navigate to={ROUTES.BENTO_PERSONA_INSTANCE} replace />;
  }

  return (
    <>
      <BentoHero
        eyebrow={
          <Link
            to={`${ROUTES.BENTO_PERSONA_INSTANCE}/${personaId}`}
            className="hover:text-foreground"
          >
            {persona?.name ?? t("persona.title")}
          </Link>
        }
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
            <IconFileSearch size={24} />
          </span>
        }
        title={t("persona.validate.title", { defaultValue: "Validate content" })}
        subtitle={t("persona.validate.subtitle", {
          defaultValue:
            "Add documents, links or indexed content and score how well it fits this persona's audience.",
        })}
      />

      <SectionCardChromeProvider chrome="bento">
        <div className="mt-6 flex flex-col gap-5">
          <PersonaSourcesSection personaId={personaId} />
          <PersonaFitReportSection personaId={personaId} />
        </div>
      </SectionCardChromeProvider>
    </>
  );
}
