import { ChatStarter } from "@/app/console/chat/components/chat-starter";
import { ROUTES } from "@/app/routes.const";
import { BentoHero, BentoSection, BentoTile, type BentoTone } from "@/components/bento";
import { TurLogo } from "@/components/logo/tur-logo";
import { useCurrentUser } from "@/contexts/user.context";
import type { TurAIAgent } from "@/models/agent/ai-agent.model";
import { TurAIAgentService } from "@/services/agent/ai-agent.service";
import { TurLLMInstanceService } from "@/services/llm/llm.service";
import { TurGlobalSettingsService } from "@/services/system/global-settings.service";
import {
  IconBrandGraphql,
  IconChartBar,
  IconCompass,
  IconCpu2,
  IconCube,
  IconDatabase,
  IconFileImport,
  IconFolder,
  IconMessageChatbot,
  IconPlugConnectedX,
  IconReceiptRupee,
  IconRobot,
  IconServer2,
  IconShieldCog,
  IconUserCircle,
  IconZoomCode,
} from "@tabler/icons-react";
import type { ComponentType } from "react";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

const turLLMInstanceService = new TurLLMInstanceService();
const turGlobalSettingsService = new TurGlobalSettingsService();
const turAIAgentService = new TurAIAgentService();

interface FeatureItem {
  titleKey: string;
  descriptionKey: string;
  url: string;
  icon: ComponentType<{ size?: number }>;
  tone: BentoTone;
  span: string;
  privilege?: string;
  /** Hide unless at least one LLM instance is enabled. */
  requiresLlm?: boolean;
}

interface FeatureSection {
  eyebrowKey: string;
  titleKey: string;
  descriptionKey: string;
  items: FeatureItem[];
}

const SPAN_FEATURED = "col-span-2 row-span-2 md:col-span-2 md:row-span-2 lg:col-span-2 lg:row-span-2";
const SPAN_WIDE     = "col-span-2 row-span-1 md:col-span-2 md:row-span-1 lg:col-span-2 lg:row-span-1";
const SPAN_SQUARE   = "col-span-1 row-span-1";

/**
 * The mosaic mixes featured (2x2), wide (2x1) and square (1x1) tiles to
 * give each section a curated, magazine-like rhythm rather than a uniform
 * card grid.
 */
const SECTIONS: FeatureSection[] = [
  {
    eyebrowKey: "home.title",
    titleKey: "home.sections.generativeAi.label",
    descriptionKey: "home.sections.generativeAi.description",
    items: [
      // AI Agent is the gateway to the bento experience itself — featured.
      { titleKey: "home.features.aiAgent.title",       descriptionKey: "home.features.aiAgent.description",       url: ROUTES.BENTO_AI_AGENT_INSTANCE, icon: IconRobot,           tone: "blue",    span: SPAN_FEATURED, privilege: "AI_AGENT_VIEW" },
      { titleKey: "home.features.languageModel.title", descriptionKey: "home.features.languageModel.description", url: ROUTES.BENTO_LLM_INSTANCE,      icon: IconCpu2,            tone: "indigo",  span: SPAN_WIDE,     privilege: "LLM_VIEW" },
      { titleKey: "home.features.embeddingModel.title", descriptionKey: "home.features.embeddingModel.description", url: ROUTES.EMBEDDING_MODEL_INSTANCE, icon: IconCube,         tone: "violet",  span: SPAN_SQUARE,   privilege: "EMBEDDING_VIEW" },
      { titleKey: "home.features.embeddingStore.title", descriptionKey: "home.features.embeddingStore.description", url: "/admin/store/instance",       icon: IconDatabase,        tone: "violet",  span: SPAN_SQUARE,   privilege: "STORE_VIEW" },
      { titleKey: "home.features.mcpServer.title",     descriptionKey: "home.features.mcpServer.description",     url: "/admin/mcp/instance",          icon: IconServer2,         tone: "amber",   span: SPAN_SQUARE },
      // Personas are voice profiles applied to AI agents — sit alongside the
      // other agent-adjacent configs (MCP, tokens) in the same row.
      { titleKey: "home.features.persona.title",       descriptionKey: "home.features.persona.description",       url: ROUTES.PERSONA_INSTANCE,        icon: IconUserCircle,      tone: "rose",    span: SPAN_SQUARE },
      { titleKey: "home.features.tokenUsage.title",    descriptionKey: "home.features.tokenUsage.description",    url: ROUTES.TOKEN_USAGE,             icon: IconChartBar,        tone: "emerald", span: SPAN_SQUARE },
      // Chat lives at the top of the page already (QuickChat tile), so the
      // grid entry stays as a 1x1 navigation shortcut rather than a 2x1 hero.
      { titleKey: "home.features.chat.title",          descriptionKey: "home.features.chat.description",          url: ROUTES.CHAT_ROOT,               icon: IconMessageChatbot,  tone: "indigo",  span: SPAN_SQUARE },
    ],
  },
  {
    eyebrowKey: "home.title",
    titleKey: "home.sections.enterpriseSearch.label",
    descriptionKey: "home.sections.enterpriseSearch.description",
    items: [
      { titleKey: "home.features.semanticNavigation.title", descriptionKey: "home.features.semanticNavigation.description", url: "/admin/sn/instance",   icon: IconCompass,        tone: "emerald", span: SPAN_FEATURED, privilege: "SN_VIEW" },
      { titleKey: "home.features.searchEngine.title",       descriptionKey: "home.features.searchEngine.description",       url: "/admin/se/instance",   icon: IconZoomCode,       tone: "emerald", span: SPAN_WIDE,     privilege: "SE_VIEW" },
      { titleKey: "home.features.integration.title",        descriptionKey: "home.features.integration.description",        url: "/admin/integration/instance", icon: IconPlugConnectedX, tone: "amber",   span: SPAN_SQUARE },
      { titleKey: "home.features.graphqlExplorer.title",    descriptionKey: "home.features.graphqlExplorer.description",    url: ROUTES.GRAPHQL_ROOT,    icon: IconBrandGraphql,   tone: "rose",    span: SPAN_SQUARE },
    ],
  },
  {
    eyebrowKey: "home.title",
    titleKey: "home.sections.management.label",
    descriptionKey: "home.sections.management.description",
    items: [
      { titleKey: "home.features.administration.title", descriptionKey: "home.features.administration.description", url: ROUTES.ADMIN_ROOT,        icon: IconShieldCog,    tone: "slate",   span: SPAN_WIDE },
      { titleKey: "home.features.assets.title",         descriptionKey: "home.features.assets.description",         url: ROUTES.ASSET_ROOT,        icon: IconFolder,       tone: "amber",   span: SPAN_SQUARE },
      { titleKey: "home.features.import.title",         descriptionKey: "home.features.import.description",         url: "/admin/exchange/import", icon: IconFileImport,   tone: "indigo",  span: SPAN_SQUARE },
      { titleKey: "home.features.logging.title",        descriptionKey: "home.features.logging.description",        url: "/admin/logging/instance", icon: IconReceiptRupee, tone: "rose",    span: SPAN_SQUARE },
    ],
  },
];

function getGreetingKey(): string {
  const hour = new Date().getHours();
  if (hour < 12) return "home.greeting.morning";
  if (hour < 18) return "home.greeting.afternoon";
  return "home.greeting.evening";
}

export default function BentoHomePage() {
  const { t } = useTranslation();
  const { user } = useCurrentUser();
  const isAdmin = Boolean(user?.admin);
  const userPrivileges = useMemo(() => new Set(user?.privileges ?? []), [user?.privileges]);

  const [hasEnabledLlm, setHasEnabledLlm] = useState(false);
  const [defaultAgent, setDefaultAgent] = useState<TurAIAgent | null>(null);
  const [chatLlmId, setChatLlmId] = useState<string | null>(null);

  // Same data wiring as the legacy home page — fetches default agent + LLMs
  // to decide whether to show the inline ChatStarter.
  useEffect(() => {
    Promise.all([
      turLLMInstanceService.query().catch(() => []),
      turGlobalSettingsService.query().catch(() => null),
    ]).then(async ([instances, settings]) => {
      const enabledLlms = instances.filter((i) => i.enabled === 1);
      setHasEnabledLlm(enabledLlms.length > 0);

      const defaultAgentId = settings?.defaultAiAgentId;
      if (!defaultAgentId) return;

      try {
        const agent = await turAIAgentService.get(defaultAgentId);
        if (agent?.enabled !== 1) return;
        const enabledLlmIds = new Set(enabledLlms.map((i) => i.id));
        const agentLlm = agent.llmInstances.find((l) => enabledLlmIds.has(l.id));
        if (!agentLlm) return;
        setDefaultAgent(agent);
        setChatLlmId(agentLlm.id);
      } catch (err) {
        console.warn("Failed to load default AI agent for home chat:", err);
      }
    });
  }, []);

  const visibleSections = useMemo(
    () =>
      SECTIONS.map((sec) => ({
        ...sec,
        items: sec.items.filter((item) => {
          if (item.requiresLlm && !hasEnabledLlm) return false;
          if (!isAdmin && item.privilege && !userPrivileges.has(item.privilege)) return false;
          return true;
        }),
      })).filter((sec) => sec.items.length > 0),
    [hasEnabledLlm, isAdmin, userPrivileges],
  );

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

      {visibleSections.map((section) => (
        <BentoSection
          key={section.titleKey}
          title={t(section.titleKey)}
          description={t(section.descriptionKey)}
        >
          {section.items.map((item) => (
            <BentoTile
              key={item.titleKey}
              to={item.url}
              icon={item.icon}
              tone={item.tone}
              eyebrow={t(section.titleKey)}
              title={t(item.titleKey)}
              span={item.span}
              featured={item.span === SPAN_FEATURED}
            >
              <p className="text-sm text-muted-foreground">
                {t(item.descriptionKey)}
              </p>
            </BentoTile>
          ))}
        </BentoSection>
      ))}
    </>
  );
}
