import { ChatStarter } from "@/app/console/chat/components/chat-starter";
import { useLlmInstances } from "@/api/queries/llm-instance.queries";
import {
  BENTO_SPAN_FEATURED,
  BENTO_SPAN_SQUARE,
  BentoHero,
  BentoSection,
  BentoTile,
  bentoNavTarget,
  useVisibleBentoSections,
} from "@/components/bento";
import { TurLogo } from "@/components/logo/tur-logo";
import { useCurrentUser } from "@/contexts/user.context";
import type { TurAIAgent } from "@/models/agent/ai-agent.model";
import { TurAIAgentService } from "@/services/agent/ai-agent.service";
import { TurGlobalSettingsService } from "@/services/system/global-settings.service";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

const turGlobalSettingsService = new TurGlobalSettingsService();
const turAIAgentService = new TurAIAgentService();

function getGreetingKey(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "home.greeting.morning";
  if (hour < 18) return "home.greeting.afternoon";
  return "home.greeting.evening";
}

/**
 * The Bento home — the "overview" root of the hub-and-spoke IA (T573). It reads
 * the same unified nav config as the rail, the area hubs, and the ⌘K palette,
 * rendering the three sections (Generative AI · Enterprise Search · Management)
 * as bento mosaics. Each section heading links to its dedicated area hub, so
 * the home is a jumping-off overview while the rail stays a slim set of hubs.
 */
export default function BentoHomePage() {
  const { t } = useTranslation();
  const { user } = useCurrentUser();

  const { data: llmInstances } = useLlmInstances();
  const hasEnabledLlm = useMemo(
    () => (llmInstances ?? []).some((i) => i.enabled === 1),
    [llmInstances],
  );

  const [defaultAgent, setDefaultAgent] = useState<TurAIAgent | null>(null);
  const [chatLlmId, setChatLlmId] = useState<string | null>(null);

  // Overview mosaics: the visible sections that own a hub route (drops the
  // `primary` section — home/dashboard aren't overview tiles) and, within
  // Generative AI, hides the Chat tile until an LLM is enabled.
  const sections = useVisibleBentoSections();
  const overviewSections = useMemo(
    () =>
      sections
        .filter((g) => g.section.areaRoute)
        .map((g) => ({
          ...g,
          items: g.items.filter((i) => !i.requiresLlm || hasEnabledLlm),
        }))
        .filter((g) => g.items.length > 0),
    [sections, hasEnabledLlm],
  );

  // Decide whether to show the inline ChatStarter (default agent + enabled LLM).
  useEffect(() => {
    Promise.all([
      turGlobalSettingsService.query().catch(() => null),
    ]).then(async ([settings]) => {
      const defaultAgentId = settings?.defaultAiAgentId;
      if (!defaultAgentId || !hasEnabledLlm) return;

      try {
        const agent = await turAIAgentService.get(defaultAgentId);
        if (agent?.enabled !== 1) return;
        const enabledLlmIds = new Set((llmInstances ?? []).filter((i) => i.enabled === 1).map((i) => i.id));
        const agentLlm = agent.llmInstances.find((l) => enabledLlmIds.has(l.id));
        if (!agentLlm) return;
        setDefaultAgent(agent);
        setChatLlmId(agentLlm.id);
      } catch (err) {
        console.warn("Failed to load default AI agent for home chat:", err);
      }
    });
  }, [hasEnabledLlm, llmInstances]);

  const firstName = user?.firstName || user?.username || "there";

  return (
    <>
      {/*
       * No eyebrow on home — this is the root of the bento tree, so
       * there's no parent section to breadcrumb back to. The greeting
       * itself is the headline.
       */}
      <BentoHero
        leading={<TurLogo className="size-12" />}
        title={<>{t(getGreetingKey())}, {firstName}.</>}
        subtitle={t("home.welcome")}
      />

      {chatLlmId && defaultAgent && (
        <div className="bento-tile bento-glass mb-8 rounded-3xl p-4 md:mb-10 md:p-6">
          <ChatStarter
            defaultLlmId={chatLlmId}
            agentId={defaultAgent.id}
            modelLabel={defaultAgent.title}
            showHeader={false}
          />
        </div>
      )}

      {overviewSections.map(({ section, items }) => {
        const label = t(section.labelKey ?? "");
        return (
          <BentoSection
            key={section.id}
            title={label}
            description={section.descriptionKey ? t(section.descriptionKey) : undefined}
            titleHref={section.areaRoute}
          >
            {items.map((item) => (
              <BentoTile
                key={item.id}
                to={bentoNavTarget(item)}
                icon={item.icon}
                tone={item.tone}
                eyebrow={label}
                title={t(item.titleKey)}
                span={item.span ?? BENTO_SPAN_SQUARE}
                featured={item.span === BENTO_SPAN_FEATURED}
              >
                <p className="text-sm text-muted-foreground">{t(item.descriptionKey)}</p>
              </BentoTile>
            ))}
          </BentoSection>
        );
      })}
    </>
  );
}
