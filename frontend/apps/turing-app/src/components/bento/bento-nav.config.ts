import { ROUTES } from "@/app/routes.const";
import { useCurrentUser } from "@/contexts/user.context";
import type { Icon as TablerIcon } from "@tabler/icons-react";
import {
  IconBraces,
  IconBrandGraphql,
  IconBuildingStore,
  IconClock,
  IconCompass,
  IconCpu2,
  IconCube,
  IconDatabase,
  IconFolder,
  IconGitBranch,
  IconGlobe,
  IconHome,
  IconLayoutDashboard,
  IconMessageChatbot,
  IconPlugConnectedX,
  IconReportAnalytics,
  IconRobot,
  IconServer2,
  IconShieldCog,
  IconSparkles,
  IconUserCircle,
  IconWebhook,
  IconZoomCode,
} from "@tabler/icons-react";
import { useMemo } from "react";

/** Top-level grouping for the Bento nav rail + command palette. */
export type BentoNavSectionId = "primary" | "generativeAi" | "enterpriseSearch" | "management";

export interface BentoNavSection {
  id: BentoNavSectionId;
  /** i18n key for the section label. `primary` renders unlabelled. */
  labelKey?: string;
}

export const BENTO_NAV_SECTIONS: readonly BentoNavSection[] = [
  { id: "primary" },
  { id: "generativeAi", labelKey: "home.sections.generativeAi.label" },
  { id: "enterpriseSearch", labelKey: "home.sections.enterpriseSearch.label" },
  { id: "management", labelKey: "home.sections.management.label" },
];

export interface BentoNavItem {
  id: string;
  /** i18n key for the item label. */
  titleKey: string;
  icon: TablerIcon;
  section: BentoNavSectionId;
  /**
   * Bento route once this surface is migrated. The nav rail lists **only**
   * items that have one; the command palette links here when present.
   */
  bentoRoute?: string;
  /**
   * Console route the palette falls back to while a surface is not yet
   * migrated — so `⌘K` reaches every area during the migration (per ADR
   * 0002, the console stays live until Phase 5). When a surface migrates,
   * set `bentoRoute` and it graduates onto the rail automatically.
   */
  consoleRoute: string;
  /** Privilege required to see the item; admins bypass. Undefined = always. */
  privilege?: string;
}

/**
 * The single source of truth for Bento navigation (T549), mirroring the
 * console `AppSidebar` taxonomy. As each Block AG phase migrates a surface,
 * fill in its `bentoRoute` here and it appears on the rail without touching
 * the rail or palette components.
 */
export const BENTO_NAV_ITEMS: readonly BentoNavItem[] = [
  // --- primary ---
  { id: "home", titleKey: "home.title", icon: IconHome, section: "primary", bentoRoute: ROUTES.BENTO_HOME, consoleRoute: ROUTES.HOME },
  { id: "dashboard", titleKey: "dashboard.title", icon: IconLayoutDashboard, section: "primary", consoleRoute: ROUTES.DASHBOARD },

  // --- generative AI ---
  { id: "aiAgent", titleKey: "home.features.aiAgent.title", icon: IconRobot, section: "generativeAi", bentoRoute: ROUTES.BENTO_AI_AGENT_INSTANCE, consoleRoute: "/admin/ai-agent/instance", privilege: "AI_AGENT_VIEW" },
  { id: "languageModel", titleKey: "home.features.languageModel.title", icon: IconCpu2, section: "generativeAi", bentoRoute: ROUTES.BENTO_LLM_INSTANCE, consoleRoute: "/admin/llm/instance", privilege: "LLM_VIEW" },
  { id: "embeddingModel", titleKey: "home.features.embeddingModel.title", icon: IconCube, section: "generativeAi", consoleRoute: ROUTES.EMBEDDING_MODEL_INSTANCE, privilege: "EMBEDDING_VIEW" },
  { id: "embeddingStore", titleKey: "home.features.embeddingStore.title", icon: IconDatabase, section: "generativeAi", consoleRoute: "/admin/store/instance", privilege: "STORE_VIEW" },
  { id: "mcpServer", titleKey: "home.features.mcpServer.title", icon: IconServer2, section: "generativeAi", consoleRoute: "/admin/mcp/instance" },
  { id: "customTool", titleKey: "home.features.customTool.title", icon: IconBraces, section: "generativeAi", consoleRoute: ROUTES.CUSTOM_TOOL_INSTANCE },
  { id: "routine", titleKey: "home.features.routine.title", icon: IconClock, section: "generativeAi", consoleRoute: ROUTES.ROUTINE_INSTANCE },
  { id: "chatWebhook", titleKey: "home.features.chatWebhook.title", icon: IconWebhook, section: "generativeAi", consoleRoute: ROUTES.CHAT_WEBHOOK_INSTANCE },
  { id: "persona", titleKey: "home.features.persona.title", icon: IconUserCircle, section: "generativeAi", consoleRoute: ROUTES.PERSONA_INSTANCE, privilege: "AI_AGENT_VIEW" },
  { id: "skills", titleKey: "home.features.skills.title", icon: IconSparkles, section: "generativeAi", consoleRoute: ROUTES.SKILL_ROOT },
  { id: "aiAnalytics", titleKey: "home.features.aiAnalytics.title", icon: IconReportAnalytics, section: "generativeAi", consoleRoute: ROUTES.AI_ANALYTICS_ROOT },
  { id: "chat", titleKey: "home.features.chat.title", icon: IconMessageChatbot, section: "generativeAi", consoleRoute: ROUTES.CHAT_ROOT },

  // --- enterprise search ---
  { id: "searchEngine", titleKey: "home.features.searchEngine.title", icon: IconZoomCode, section: "enterpriseSearch", consoleRoute: "/admin/se/instance", privilege: "SE_VIEW" },
  { id: "semanticNavigation", titleKey: "home.features.semanticNavigation.title", icon: IconCompass, section: "enterpriseSearch", consoleRoute: "/admin/sn/instance", privilege: "SN_VIEW" },
  { id: "integration", titleKey: "home.features.integration.title", icon: IconPlugConnectedX, section: "enterpriseSearch", consoleRoute: "/admin/integration/instance" },
  { id: "graphqlExplorer", titleKey: "home.features.graphqlExplorer.title", icon: IconBrandGraphql, section: "enterpriseSearch", consoleRoute: ROUTES.GRAPHQL_ROOT },

  // --- management ---
  { id: "assets", titleKey: "home.features.assets.title", icon: IconFolder, section: "management", consoleRoute: ROUTES.ASSET_ROOT },
  { id: "pages", titleKey: "home.features.pages.title", icon: IconGlobe, section: "management", consoleRoute: ROUTES.PAGE_ROOT },
  { id: "git", titleKey: "home.features.git.title", icon: IconGitBranch, section: "management", consoleRoute: ROUTES.GIT_ROOT },
  { id: "marketplace", titleKey: "home.features.marketplace.title", icon: IconBuildingStore, section: "management", consoleRoute: ROUTES.MARKETPLACE_ROOT },
  { id: "administration", titleKey: "home.features.administration.title", icon: IconShieldCog, section: "management", consoleRoute: ROUTES.ADMIN_ROOT, privilege: "__admin__" },
];

/**
 * Privilege-filtered nav items for the current user (admins see all). The
 * synthetic `__admin__` privilege gates admin-only areas. Feature-flag
 * gating (storage/git/marketplace) is intentionally deferred to the target
 * route itself — the palette links out to the console for unmigrated areas,
 * which already enforce those flags.
 */
export function useVisibleBentoNav(): BentoNavItem[] {
  const { user } = useCurrentUser();
  const isAdmin = !!user?.admin;
  const privileges = useMemo(() => new Set(user?.privileges ?? []), [user?.privileges]);

  return useMemo(
    () =>
      BENTO_NAV_ITEMS.filter((item) => {
        if (!item.privilege) return true;
        if (isAdmin) return true;
        if (item.privilege === "__admin__") return false;
        return privileges.has(item.privilege);
      }),
    [isAdmin, privileges],
  );
}

/** Resolve where an item navigates: bento route if migrated, else console. */
export function bentoNavTarget(item: BentoNavItem): string {
  return item.bentoRoute ?? item.consoleRoute;
}
