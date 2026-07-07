import { CHAT_INITIAL_PROMPT_KEY } from "@/app/console/chat/chat.types";
import { ROUTES } from "@/app/routes.const";
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts";
import { IconMessageChatbot } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

/**
 * Bento AI-agent "launch" bar — mirrors the SN detail launch bar (T-SN). The
 * primary thing you do with a configured agent is talk to it, so surface a
 * frosted "Open chat" launcher on the agent detail dashboard. It navigates to
 * the deterministic, shareable `/bento/chat/agent/:id` route (T580, replacing
 * the retired `CHAT_INITIAL_AGENT_KEY` sessionStorage hand-off) and clears any
 * stale forwarded prompt.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export function AiAgentLaunchBar({ agent }: Readonly<{ agent: TurAIAgent }>) {
  const { t } = useTranslation();
  const navigate = useNavigate();

  function openChat() {
    sessionStorage.removeItem(CHAT_INITIAL_PROMPT_KEY);
    navigate(`${ROUTES.BENTO_CHAT_AGENT}/${agent.id}`);
  }

  return (
    <section className="mb-6">
      <h2 className="mb-3 flex items-center gap-2 text-sm font-medium text-muted-foreground">
        <IconMessageChatbot size={16} className="text-blue-500" />
        {t("aiAgent.launch.title", { defaultValue: "Launch" })}
      </h2>
      <button
        type="button"
        onClick={openChat}
        className="bento-tile bento-tile-clickable bento-glass inline-flex items-center gap-2.5 rounded-2xl px-4 py-2.5 text-sm font-medium"
      >
        <span className="grid h-8 w-8 shrink-0 place-items-center rounded-xl bg-linear-to-br from-blue-600 to-indigo-600 text-white shadow-sm">
          <IconMessageChatbot size={16} />
        </span>
        {t("aiAgent.launch.openChat", { defaultValue: "Open chat" })}
      </button>
    </section>
  );
}
