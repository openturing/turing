import { BentoHero } from "@/components/bento";
import PersonaChatPage from "@/app/console/chat/persona-chat.page";
import { IconMasksTheater } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { Navigate, useParams } from "react-router-dom";
import { ROUTES } from "@/app/routes.const";

/**
 * Bento persona chat workspace — Block AI / §XXXII.4 (T580/T581). Reached from
 * `/bento/chat/persona/:personaId`. Reuses {@link PersonaChatPage} under the
 * frosted Bento header (same "sits inside the shell" treatment as the agent
 * chat, per §XXXI.8); it is given a {@link BentoHero} page header rendered in
 * every state, like the other Bento pages. A missing id redirects to the
 * persona list.
 */
export default function BentoPersonaChatPage() {
  const { t } = useTranslation();
  const { personaId } = useParams<{ personaId: string }>();

  if (!personaId) {
    return <Navigate to={ROUTES.BENTO_PERSONA_INSTANCE} replace />;
  }

  return (
    <PersonaChatPage
      key={personaId}
      personaId={personaId}
      emptyStateHeader={
        <BentoHero
          backTo={ROUTES.BENTO_AREA_GENERATIVE_AI}
          backLabel={t("home.sections.generativeAi.label")}
          leading={
            <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-fuchsia-500 to-pink-600 text-white shadow-md">
              <IconMasksTheater size={24} />
            </span>
          }
          title={t("persona.title", { defaultValue: "Persona" })}
          subtitle={t("chat.persona.subtitle", {
            defaultValue: "Talk directly to a persona to test its voice",
          })}
        />
      }
    />
  );
}
