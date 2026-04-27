"use client"
import { ChatStarter } from "@/app/console/chat/components/chat-starter"
import { ROUTES } from "@/app/routes.const"
import { TurLogo } from "@/components/logo/tur-logo"
import { PageHeader } from "@/components/page-header"
import { useCurrentUser } from "@/contexts/user.context"
import type { TurAIAgent } from "@/models/agent/ai-agent.model"
import { TurAIAgentService } from "@/services/agent/ai-agent.service"
import { TurLLMInstanceService } from "@/services/llm/llm.service"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"
import type { Icon } from "@tabler/icons-react"
import {
  IconBook,
  IconBrandGraphql,
  IconChartBar,
  IconCompass,
  IconCpu2,
  IconCube,
  IconDatabase,
  IconExternalLink,
  IconFileImport,
  IconFolder,
  IconHome,
  IconMessageChatbot,
  IconPlugConnectedX,
  IconReceiptRupee,
  IconRobot,
  IconServer2,
  IconShieldCog,
  IconZoomCode
} from "@tabler/icons-react"
import { useEffect, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { NavLink } from "react-router-dom"

const turLLMInstanceService = new TurLLMInstanceService()
const turGlobalSettingsService = new TurGlobalSettingsService()
const turAIAgentService = new TurAIAgentService()

interface FeatureCard {
  titleKey: string
  descriptionKey: string
  url: string
  icon: Icon
  privilege?: string
  /** Only show when at least one LLM instance is enabled */
  requiresLlm?: boolean
  /** External documentation URL */
  docUrl?: string
}

interface FeatureSection {
  labelKey: string
  descriptionKey: string
  gradient: string
  items: FeatureCard[]
}

const SECTIONS: FeatureSection[] = [
  {
    labelKey: "home.sections.generativeAi.label",
    descriptionKey: "home.sections.generativeAi.description",
    gradient: "from-blue-600 to-indigo-600",
    items: [
      { titleKey: "home.features.languageModel.title", descriptionKey: "home.features.languageModel.description", url: "/admin/llm/instance", icon: IconCpu2, privilege: "LLM_VIEW", docUrl: "https://docs.viglet.org/turing/llm-instances" },
      { titleKey: "home.features.embeddingModel.title", descriptionKey: "home.features.embeddingModel.description", url: ROUTES.EMBEDDING_MODEL_INSTANCE, icon: IconCube, privilege: "EMBEDDING_VIEW", docUrl: "https://docs.viglet.org/turing/embedding-models" },
      { titleKey: "home.features.embeddingStore.title", descriptionKey: "home.features.embeddingStore.description", url: "/admin/store/instance", icon: IconDatabase, privilege: "STORE_VIEW", docUrl: "https://docs.viglet.org/turing/embedding-stores" },
      { titleKey: "home.features.mcpServer.title", descriptionKey: "home.features.mcpServer.description", url: "/admin/mcp/instance", icon: IconServer2, docUrl: "https://docs.viglet.org/turing/mcp-servers" },
      { titleKey: "home.features.aiAgent.title", descriptionKey: "home.features.aiAgent.description", url: "/admin/ai-agent/instance", icon: IconRobot, privilege: "AI_AGENT_VIEW", docUrl: "https://docs.viglet.org/turing/ai-agents" },
      { titleKey: "home.features.tokenUsage.title", descriptionKey: "home.features.tokenUsage.description", url: ROUTES.TOKEN_USAGE, icon: IconChartBar, docUrl: "https://docs.viglet.org/turing/token-usage" },
      { titleKey: "home.features.chat.title", descriptionKey: "home.features.chat.description", url: ROUTES.CHAT_ROOT, icon: IconMessageChatbot, docUrl: "https://docs.viglet.org/turing/chat" },
    ],
  },
  {
    labelKey: "home.sections.enterpriseSearch.label",
    descriptionKey: "home.sections.enterpriseSearch.description",
    gradient: "from-emerald-600 to-teal-600",
    items: [
      { titleKey: "home.features.searchEngine.title", descriptionKey: "home.features.searchEngine.description", url: "/admin/se/instance", icon: IconZoomCode, privilege: "SE_VIEW", docUrl: "https://docs.viglet.org/turing/search-engine" },
      { titleKey: "home.features.semanticNavigation.title", descriptionKey: "home.features.semanticNavigation.description", url: "/admin/sn/instance", icon: IconCompass, privilege: "SN_VIEW", docUrl: "https://docs.viglet.org/turing/semantic-navigation" },
      { titleKey: "home.features.integration.title", descriptionKey: "home.features.integration.description", url: "/admin/integration/instance", icon: IconPlugConnectedX, docUrl: "https://docs.viglet.org/turing/integration" },
      { titleKey: "home.features.graphqlExplorer.title", descriptionKey: "home.features.graphqlExplorer.description", url: ROUTES.GRAPHQL_ROOT, icon: IconBrandGraphql, docUrl: "https://docs.viglet.org/turing/graphql" },
    ],
  },
  {
    labelKey: "home.sections.management.label",
    descriptionKey: "home.sections.management.description",
    gradient: "from-slate-600 to-slate-700",
    items: [
      { titleKey: "home.features.assets.title", descriptionKey: "home.features.assets.description", url: ROUTES.ASSET_ROOT, icon: IconFolder, docUrl: "https://docs.viglet.org/turing/assets" },
      { titleKey: "home.features.import.title", descriptionKey: "home.features.import.description", url: "/admin/exchange/import", icon: IconFileImport, docUrl: "https://docs.viglet.org/turing/import-export" },
      { titleKey: "home.features.logging.title", descriptionKey: "home.features.logging.description", url: "/admin/logging/instance", icon: IconReceiptRupee, docUrl: "https://docs.viglet.org/turing/logging" },
      { titleKey: "home.features.administration.title", descriptionKey: "home.features.administration.description", url: ROUTES.ADMIN_ROOT, icon: IconShieldCog, docUrl: "https://docs.viglet.org/turing/administration-guide" },
    ],
  },
]

function getGreetingKey(): string {
  const hour = new Date().getHours()
  if (hour < 12) return "home.greeting.morning"
  if (hour < 18) return "home.greeting.afternoon"
  return "home.greeting.evening"
}

export default function HomePage() {
  const { t } = useTranslation()
  const { user } = useCurrentUser()
  const isAdmin = !!user?.admin
  const userPrivileges = useMemo(() => new Set(user?.privileges ?? []), [user?.privileges])
  const [hasEnabledLlm, setHasEnabledLlm] = useState(false)
  const [defaultAgent, setDefaultAgent] = useState<TurAIAgent | null>(null)
  const [chatLlmId, setChatLlmId] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([
      turLLMInstanceService.query().catch(() => []),
      turGlobalSettingsService.query().catch(() => null),
    ]).then(async ([instances, settings]) => {
      const enabledLlms = instances.filter((i) => i.enabled === 1)
      setHasEnabledLlm(enabledLlms.length > 0)

      const defaultAgentId = settings?.defaultAiAgentId
      if (!defaultAgentId) return

      try {
        const agent = await turAIAgentService.get(defaultAgentId)
        if (!agent || agent.enabled !== 1) return
        const enabledLlmIds = new Set(enabledLlms.map((i) => i.id))
        const agentLlm = agent.llmInstances.find((l) => enabledLlmIds.has(l.id))
        if (!agentLlm) return
        setDefaultAgent(agent)
        setChatLlmId(agentLlm.id)
      } catch (err) {
        console.warn("Failed to load default AI agent for home chat:", err)
      }
    })
  }, [])

  const visibleSections = useMemo(() =>
    SECTIONS.map((sec) => ({
      ...sec,
      items: sec.items.filter((item) => {
        if (item.requiresLlm && !hasEnabledLlm) return false
        return true
      }),
    })).filter((sec) => sec.items.length > 0),
    [hasEnabledLlm]
  )

  const canAccess = (item: FeatureCard) =>
    isAdmin || !item.privilege || userPrivileges.has(item.privilege)

  const firstName = user?.firstName || user?.username || "there"

  return (
    <>
      <PageHeader turIcon={IconHome} title={t("home.title")} />
      <div className="px-6 py-8 max-w-7xl mx-auto">
        {/* Hero */}
        <div className="mb-10">
          <div className="flex items-center gap-3 mb-2">
            <TurLogo className="size-10" />
            <div>
              <h1 className="text-3xl font-bold tracking-tight">
                {t(getGreetingKey())}, {firstName}.
              </h1>
              <p className="text-muted-foreground text-sm mt-1">
                {t("home.welcome")}
              </p>
            </div>
          </div>
        </div>

        {/* Quick Chat — only renders when a default AI agent is configured in
            Global Settings, has at least one enabled LLM, and the agent itself
            is enabled. The chat then runs through the agent (system prompt,
            tools, RAG) on the chat page. */}
        {chatLlmId && defaultAgent && (
          <div className="mb-10">
            <ChatStarter
              defaultLlmId={chatLlmId}
              agentId={defaultAgent.id}
              modelLabel={defaultAgent.title}
              showHeader={false}
            />
          </div>
        )}

        {/* Sections */}
        <div className="space-y-10">
          {visibleSections.map((section) => (
            <section key={section.labelKey}>
              <div className="mb-4">
                <h2 className="text-xl font-semibold">{t(section.labelKey)}</h2>
                <p className="text-sm text-muted-foreground mt-0.5">{t(section.descriptionKey)}</p>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                {section.items.map((item) => {
                  const ItemIcon = item.icon
                  return (
                    <div key={item.titleKey} className="relative flex flex-col h-full rounded-xl border bg-card p-5 transition-all duration-200">
                      <div className={`inline-flex rounded-lg bg-gradient-to-br ${section.gradient} p-2.5 mb-3 self-start`}>
                        <ItemIcon className="size-5 text-white" />
                      </div>
                      <h3 className="font-medium text-sm mb-1">
                        {t(item.titleKey)}
                      </h3>
                      <p className="text-xs text-muted-foreground leading-relaxed mb-4 flex-1">
                        {t(item.descriptionKey)}
                      </p>
                      <div className="flex items-center justify-between pt-2 border-t border-border/50">
                        {item.docUrl ? (
                          <a
                            href={item.docUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
                          >
                            <IconBook className="size-3.5" />
                            {t("home.docs")}
                          </a>
                        ) : <span />}
                        {canAccess(item) ? (
                          <NavLink
                            to={item.url}
                            className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
                          >
                            {t("home.open")}
                            <IconExternalLink className="size-3.5" />
                          </NavLink>
                        ) : <span />}
                      </div>
                    </div>
                  )
                })}
              </div>
            </section>
          ))}
        </div>

      </div>
    </>
  )
}
