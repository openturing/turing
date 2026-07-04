import { usePersonas } from "@/api/queries/persona.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { IconUserCircle } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

export default function BentoPersonaListPage() {
  const { t } = useTranslation();
  const { data: personas, isError } = usePersonas();
  const error = isError ? t("common.connectionError", { resource: t("persona.title") }) : null;

  return (
    <BentoListPage
      items={personas}
      error={error}
      tryAgainUrl={ROUTES.BENTO_PERSONA_INSTANCE}
      eyebrow={t("home.sections.generativeAi.label")}
      heroIcon={IconUserCircle}
      tone="rose"
      title={t("persona.title")}
      subtitle={t("persona.blankDescription")}
      newRoute={`${ROUTES.BENTO_PERSONA_INSTANCE}/new`}
      newLabel={t("persona.newInstance")}
      itemKey={(persona) => persona.id ?? persona.name}
      emptyTitle={t("persona.blankTitle")}
      emptyDescription={t("persona.blankDescription")}
      renderTile={(persona, featured) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_PERSONA_INSTANCE}/${persona.id}`}
          featured={featured}
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
