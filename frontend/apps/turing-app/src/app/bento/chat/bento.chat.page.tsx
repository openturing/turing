import { useAiActivation } from "@/api/queries/viglet-ai.queries";
import { BentoHero, bentoChipClass } from "@/components/bento";
import { BentoNoLlmState } from "@/components/turing-bento";
import { ROUTES } from "@/app/routes.const";
import ChatPage from "@/app/console/chat/chat.page";
import { IconMessageChatbot } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Bento chat workspace — T563. The chat surface is an app-like screen with its
 * own internal chrome (tab bar, model picker, session sidebar), so it "sits
 * inside the shell" per §XXXI.8 rather than adopting the hero/save-bar CRUD
 * pattern: the reused {@link ChatPage} renders under the frosted Bento
 * header/nav unchanged. It is given a {@link BentoHero} page header (rendered in
 * every state, like the other Bento pages) so the surface matches them and never
 * pulls the console {@code SidebarTrigger} into a shell that has no
 * SidebarProvider.
 *
 * <p>T580 — serves both `/bento/chat` (default agent) and
 * `/bento/chat/agent/:agentId` (deterministic, shareable). The optional route
 * param is threaded to {@link ChatPage} as the initial agent.
 */
export default function BentoChatPage() {
  const { t } = useTranslation();
  const { agentId } = useParams<{ agentId?: string }>();
  const { hasEnabledLlm, isResolved } = useAiActivation();

  const hero = (
    <BentoHero
      backTo={ROUTES.BENTO_AREA_GENERATIVE_AI}
      backLabel={t("home.sections.generativeAi.label")}
      leading={
        <span className={`grid h-12 w-12 place-items-center rounded-2xl ${bentoChipClass("blue")} text-white shadow-md`}>
          <IconMessageChatbot size={24} />
        </span>
      }
      title={t("home.features.chat.title")}
      subtitle={t("home.features.chat.description")}
    />
  );

  /*
   * T958 — with no model, the reused ChatPage falls back to a console blank
   * slate pointing at the console LLM form. Answer the question here instead,
   * with the same two paths the home card offers. Gated on `isResolved` so a
   * working install never blinks this on the way in.
   */
  if (isResolved && !hasEnabledLlm) {
    return <BentoNoLlmState header={hero} description={t("chat.noLlmDescription")} />;
  }

  return <ChatPage initialAgentId={agentId} emptyStateHeader={hero} />;
}
