import { BentoHero } from "@/components/bento";
import ChatPage from "@/app/console/chat/chat.page";
import { IconMessageChatbot } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Bento chat workspace — T563. The chat surface is an app-like screen with its
 * own internal chrome (tab bar, model picker, session sidebar), so it "sits
 * inside the shell" per §XXXI.8 rather than adopting the hero/save-bar CRUD
 * pattern: the reused {@link ChatPage} renders under the frosted Bento
 * header/nav unchanged. Only the empty-state header is swapped for a
 * {@link BentoHero} so the reused surface never pulls the console
 * {@code SidebarTrigger} into a shell that has no SidebarProvider.
 *
 * <p>T580 — serves both `/bento/chat` (default agent) and
 * `/bento/chat/agent/:agentId` (deterministic, shareable). The optional route
 * param is threaded to {@link ChatPage} as the initial agent.
 */
export default function BentoChatPage() {
  const { t } = useTranslation();
  const { agentId } = useParams<{ agentId?: string }>();
  return (
    <ChatPage
      initialAgentId={agentId}
      emptyStateHeader={
        <BentoHero
          eyebrow={t("home.sections.generativeAi.label")}
          leading={
            <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-blue-500 to-indigo-600 text-white shadow-md">
              <IconMessageChatbot size={24} />
            </span>
          }
          title={t("home.features.chat.title")}
          subtitle={t("home.features.chat.description")}
        />
      }
    />
  );
}
