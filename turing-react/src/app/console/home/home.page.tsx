"use client"
import { ChatStarter } from "@/app/console/chat/components/chat-starter"
import { ROUTES } from "@/app/routes.const"
import { TurLogo } from "@/components/logo/tur-logo"
import { PageHeader } from "@/components/page-header"
import { useCurrentUser } from "@/contexts/user.context"
import { TurLLMInstanceService } from "@/services/llm/llm.service"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"
import type { Icon } from "@tabler/icons-react"
import {
  IconBook,
  IconBrandGraphql,
  IconBulb,
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
import { NavLink } from "react-router-dom"

const turLLMInstanceService = new TurLLMInstanceService()
const turGlobalSettingsService = new TurGlobalSettingsService()

interface FeatureCard {
  title: string
  description: string
  url: string
  icon: Icon
  privilege?: string
  /** Only show when at least one LLM instance is enabled */
  requiresLlm?: boolean
  /** External documentation URL */
  docUrl?: string
}

interface FeatureSection {
  label: string
  description: string
  gradient: string
  items: FeatureCard[]
}

const SECTIONS: FeatureSection[] = [
  {
    label: "Generative AI",
    description: "Build, configure and interact with large language models, embeddings, and intelligent agents.",
    gradient: "from-blue-600 to-indigo-600",
    items: [
      { title: "Language Model", description: "Configure LLM providers like OpenAI, Ollama, Anthropic, and Gemini for chat and generation.", url: "/admin/llm/instance", icon: IconCpu2, privilege: "LLM_VIEW", docUrl: "https://docs.viglet.com/turing/llm-instances" },
      { title: "Embedding Model", description: "Set up embedding models for vector similarity search and RAG pipelines.", url: ROUTES.EMBEDDING_MODEL_INSTANCE, icon: IconCube, privilege: "EMBEDDING_VIEW", docUrl: "https://docs.viglet.com/turing/embedding-models" },
      { title: "Embedding Store", description: "Manage vector stores like Chroma, Milvus, and PGVector for semantic retrieval.", url: "/admin/store/instance", icon: IconDatabase, privilege: "STORE_VIEW", docUrl: "https://docs.viglet.com/turing/embedding-stores" },
      { title: "MCP Server", description: "Connect to Model Context Protocol servers for external tool integration.", url: "/admin/mcp/instance", icon: IconServer2, docUrl: "https://docs.viglet.com/turing/mcp-servers" },
      { title: "AI Agent", description: "Create AI agents that orchestrate LLMs, tools, and MCP servers for complex tasks.", url: "/admin/ai-agent/instance", icon: IconRobot, privilege: "AI_AGENT_VIEW", docUrl: "https://docs.viglet.com/turing/ai-agents" },
      { title: "Intent", description: "Define prompt categories and actions shown as quick suggestions in the chat.", url: ROUTES.INTENT_INSTANCE, icon: IconBulb, privilege: "INTENT_VIEW", docUrl: "https://docs.viglet.com/turing/intent" },
      { title: "Token Usage", description: "Monitor token consumption across all LLM instances and models.", url: ROUTES.TOKEN_USAGE, icon: IconChartBar, requiresLlm: true, docUrl: "https://docs.viglet.com/turing/token-usage" },
      { title: "Chat", description: "Start conversations with your configured LLMs, agents, and semantic search.", url: ROUTES.CHAT_ROOT, icon: IconMessageChatbot, requiresLlm: true, docUrl: "https://docs.viglet.com/turing/chat#chat-direct-llm" },
    ],
  },
  {
    label: "Enterprise Search",
    description: "Index, search and navigate your enterprise content with powerful search engines and semantic understanding.",
    gradient: "from-emerald-600 to-teal-600",
    items: [
      { title: "Search Engine", description: "Manage Solr, Elasticsearch, or Lucene instances that power your search.", url: "/admin/se/instance", icon: IconZoomCode, privilege: "SE_VIEW", docUrl: "https://docs.viglet.com/turing/search-engine" },
      { title: "Semantic Navigation", description: "Create search sites with facets, highlighting, spell-check and AI insights.", url: "/admin/sn/instance", icon: IconCompass, privilege: "SN_VIEW", docUrl: "https://docs.viglet.com/turing/semantic-navigation" },
      { title: "Integration", description: "Connect to external CMS, databases, and file systems to index their content.", url: "/admin/integration/instance", icon: IconPlugConnectedX, docUrl: "https://docs.viglet.com/turing/integration" },
      { title: "GraphQL Explorer", description: "Query your semantic navigation data using the interactive GraphQL playground.", url: ROUTES.GRAPHQL_ROOT, icon: IconBrandGraphql, docUrl: "https://docs.viglet.com/turing/graphql" },
    ],
  },
  {
    label: "Management",
    description: "System administration, asset management, and platform configuration.",
    gradient: "from-slate-600 to-slate-700",
    items: [
      { title: "Assets", description: "Browse and manage files stored in the MinIO object storage.", url: ROUTES.ASSET_ROOT, icon: IconFolder, docUrl: "https://docs.viglet.com/turing/assets" },
      { title: "Import", description: "Import configurations, sites, and data from exported packages.", url: "/admin/exchange/import", icon: IconFileImport, docUrl: "https://docs.viglet.com/turing/import-export" },
      { title: "Logging", description: "View indexing logs and monitor data pipeline activity.", url: "/admin/logging/instance", icon: IconReceiptRupee, docUrl: "https://docs.viglet.com/turing/logging" },
      { title: "Administration", description: "Manage users, groups, roles, API tokens, and system settings.", url: ROUTES.ADMIN_ROOT, icon: IconShieldCog, docUrl: "https://docs.viglet.com/turing/administration-guide" },
    ],
  },
]

function getGreeting(): string {
  const hour = new Date().getHours()
  if (hour < 12) return "Good morning"
  if (hour < 18) return "Good afternoon"
  return "Good evening"
}

export default function HomePage() {
  const { user } = useCurrentUser()
  const isAdmin = !!user?.admin
  const userPrivileges = useMemo(() => new Set(user?.privileges ?? []), [user?.privileges])
  const [hasEnabledLlm, setHasEnabledLlm] = useState(false)
  const [chatLlmId, setChatLlmId] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([
      turLLMInstanceService.query(),
      turGlobalSettingsService.query().catch(() => null),
    ]).then(([instances, settings]) => {
      const enabled = instances.filter((i) => i.enabled === 1)
      setHasEnabledLlm(enabled.length > 0)
      const defaultId = settings?.defaultLlmId
      if (defaultId && enabled.some((i) => i.id === defaultId)) {
        setChatLlmId(defaultId)
      } else if (enabled.length > 0) {
        setChatLlmId(enabled[0].id)
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
      <PageHeader turIcon={IconHome} title="Home" />
      <div className="px-6 py-8 max-w-7xl mx-auto">
        {/* Hero */}
        <div className="mb-10">
          <div className="flex items-center gap-3 mb-2">
            <TurLogo className="size-10" />
            <div>
              <h1 className="text-3xl font-bold tracking-tight">
                {getGreeting()}, {firstName}.
              </h1>
              <p className="text-muted-foreground text-sm mt-1">
                Welcome to Viglet Turing ES — your enterprise search intelligence platform.
              </p>
            </div>
          </div>
        </div>

        {/* Quick Chat */}
        {chatLlmId && (
          <div className="mb-10">
            <ChatStarter
              defaultLlmId={chatLlmId}
              showHeader={false}
            />
          </div>
        )}

        {/* Sections */}
        <div className="space-y-10">
          {visibleSections.map((section) => (
            <section key={section.label}>
              <div className="mb-4">
                <h2 className="text-xl font-semibold">{section.label}</h2>
                <p className="text-sm text-muted-foreground mt-0.5">{section.description}</p>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
                {section.items.map((item) => {
                  const ItemIcon = item.icon
                  return (
                    <div key={item.title} className="relative flex flex-col h-full rounded-xl border bg-card p-5 transition-all duration-200">
                      <div className={`inline-flex rounded-lg bg-gradient-to-br ${section.gradient} p-2.5 mb-3 self-start`}>
                        <ItemIcon className="size-5 text-white" />
                      </div>
                      <h3 className="font-medium text-sm mb-1">
                        {item.title}
                      </h3>
                      <p className="text-xs text-muted-foreground leading-relaxed mb-4 flex-1">
                        {item.description}
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
                            Docs
                          </a>
                        ) : <span />}
                        {canAccess(item) ? (
                          <NavLink
                            to={item.url}
                            className="inline-flex items-center gap-1.5 text-xs font-medium text-muted-foreground hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
                          >
                            Open
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
