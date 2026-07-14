import { usePersonas } from "@/api/queries/persona.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { Button } from "@/components/ui/button";
import { IconChartGridDots, IconMasksTheater, IconTargetArrow, IconUsersGroup, IconUserCircle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

export default function BentoPersonaListPage() {
  const { t } = useTranslation();
  const { data: personas, isError } = usePersonas();
  const error = isError ? t("common.connectionError", { resource: t("persona.title") }) : null;

  return (
    <BentoListPage
      items={personas}
      error={error}
      tryAgainUrl={ROUTES.BENTO_PERSONA_INSTANCE}
      backTo={ROUTES.BENTO_AREA_GENERATIVE_AI}
      backLabel={t("home.sections.generativeAi.label")}
      heroIcon={IconUserCircle}
      tone="rose"
      title={t("persona.title")}
      subtitle={t("persona.blankDescription")}
      headerAction={
        <>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_SUGGEST}>
              <IconTargetArrow size={16} />
              {t("forms.persona.suggest.trigger")}
            </Link>
          </Button>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_MATCH}>
              <IconChartGridDots size={16} />
              {t("persona.launch.personaMatch", { defaultValue: "Persona Match" })}
            </Link>
          </Button>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_DIALOGUE}>
              <IconMasksTheater size={16} />
              {t("persona.launch.personaDialogue", { defaultValue: "Persona dialogue" })}
            </Link>
          </Button>
          <Button asChild variant="outline" size="sm" className="gap-2">
            <Link to={ROUTES.BENTO_PERSONA_RESEARCH}>
              <IconUsersGroup size={16} />
              {t("persona.launch.personaResearch", { defaultValue: "Synthetic research" })}
            </Link>
          </Button>
        </>
      }
      newRoute={`${ROUTES.BENTO_PERSONA_INSTANCE}/new`}
      newLabel={t("persona.newInstance")}
      itemKey={(persona) => persona.id ?? persona.name}
      emptyTitle={t("persona.blankTitle")}
      emptyDescription={t("persona.blankDescription")}
      listId="persona"
      renderTile={(persona, emphasis) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_PERSONA_INSTANCE}/${persona.id}`}
          emphasis={emphasis}
          defaultIcon={IconUserCircle}
          tone="rose"
          title={persona.name}
          description={persona.description}
          hasStatus
          enabled={persona.enabled}
          meta={persona.personaKind && (
            <span className="rounded-full border border-border/60 bg-card/40 px-2 py-0.5 backdrop-blur">
              {persona.personaKind}
            </span>
          )}
        />
      )}
    />
  );
}
