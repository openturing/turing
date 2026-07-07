import { useCurrentUser } from "@/contexts/user.context";
import { TurLLMInstanceService } from "@/services/llm/llm.service";
import type { Icon } from "@tabler/icons-react";
import {
  IconCoin,
  IconGridDots,
  IconMessageCircle2,
  IconPlayerPause,
} from "@tabler/icons-react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

const llmService = new TurLLMInstanceService();

export interface AiAnalyticsNavItem {
  title: string;
  url: string;
  icon: Icon;
}

/**
 * Shared visibility logic for the "AI Analytics & Costs" sub-pages, consumed by
 * both the secondary-sidebar layout and the index redirect so the two never
 * disagree on which sub-page is first/visible.
 *
 * Mirrors the rules the main sidebar applies: token usage and cost governance
 * require an enabled LLM; parked conversations requires `AI_AGENT_VIEW`; chat
 * analytics is always available. `loaded` flips once the LLM probe resolves.
 */
export function useAiAnalyticsNav(): { items: AiAnalyticsNavItem[]; loaded: boolean } {
  const { t } = useTranslation();
  const { user } = useCurrentUser();
  const [hasEnabledLlm, setHasEnabledLlm] = useState(false);
  const [llmLoaded, setLlmLoaded] = useState(false);

  useEffect(() => {
    llmService
      .query()
      .then((instances) => setHasEnabledLlm(instances.some((i) => i.enabled === 1)))
      .catch(() => setHasEnabledLlm(false))
      .finally(() => setLlmLoaded(true));
  }, []);

  const isAdmin = !!user?.admin;
  const privileges = useMemo(() => new Set(user?.privileges ?? []), [user?.privileges]);

  const items = useMemo<AiAnalyticsNavItem[]>(() => {
    const hasPrivilege = (required?: string) =>
      isAdmin || !required || privileges.has(required);
    return [
      { title: t("home.features.costGovernance.title"), url: "/cost-governance", icon: IconCoin, visible: hasEnabledLlm },
      { title: t("home.features.chatAnalytics.title"), url: "/chat-analytics", icon: IconMessageCircle2, visible: true },
      { title: t("home.features.parkedConversations.title"), url: "/parked-conversations", icon: IconPlayerPause, visible: hasPrivilege("AI_AGENT_VIEW") },
      { title: t("capabilityMatrix.title"), url: "/capability-matrix", icon: IconGridDots, visible: isAdmin && hasEnabledLlm },
    ]
      .filter((item) => item.visible)
      .map(({ title, url, icon }) => ({ title, url, icon }));
  }, [t, hasEnabledLlm, isAdmin, privileges]);

  return { items, loaded: llmLoaded };
}
