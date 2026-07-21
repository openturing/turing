import { ROUTES } from "@/app/routes.const";
import { useCurrentUser } from "@/contexts/user.context";
import type { Icon as TablerIcon } from "@tabler/icons-react";
import {
  IconApi,
  IconBraces,
  IconBrandGraphql,
  IconCode,
  IconBuildingStore,
  IconClock,
  IconCompass,
  IconCpu2,
  IconDatabase,
  IconFileImport,
  IconFlask2,
  IconFolder,
  IconGitBranch,
  IconGlobe,
  IconHome,
  IconInfoCircle,
  IconLayoutDashboard,
  IconPlugConnectedX,
  IconReceiptRupee,
  IconReportAnalytics,
  IconRobot,
  IconSearch,
  IconServer2,
  IconSettings,
  IconSitemap,
  IconSparkles,
  IconUserCircle,
  IconUsers,
  IconUsersGroup,
  IconUserShield,
  IconWebhook,
  IconZoomCode,
} from "@tabler/icons-react";
import { useMemo } from "react";
import type { BentoTone } from "./bento-tones";

/** Top-level grouping for the Bento nav rail + area hubs + command palette. */
export type BentoNavSectionId = "primary" | "generativeAi" | "enterpriseSearch" | "management";

/** Tailwind grid-span presets for the bento mosaic (home + area hubs). */
export const BENTO_SPAN_FEATURED =
  "col-span-2 row-span-2 md:col-span-2 md:row-span-2 lg:col-span-2 lg:row-span-2";
export const BENTO_SPAN_WIDE =
  "col-span-2 row-span-1 md:col-span-2 md:row-span-1 lg:col-span-2 lg:row-span-1";
export const BENTO_SPAN_SQUARE = "col-span-1 row-span-1";

export interface BentoNavSection {
  id: BentoNavSectionId;
  /** i18n key for the section label. `primary` renders unlabelled. */
  labelKey?: string;
  /** i18n key for the section description (shown on the area hub + home). */
  descriptionKey?: string;
  /** Icon shown for this section on the nav rail. */
  icon?: TablerIcon;
  /**
   * The section "hub" page (T573). The nav rail links here; the hub renders
   * every visible item of the section as a bento mosaic. `primary` has none —
   * its single home item is the rail's Home button and the page root itself.
   */
  areaRoute?: string;
}

/**
 * The four navigation sections. The rail lists **Home + the three hubs that
 * have an `areaRoute`** — never the ~22 leaf surfaces (T573). This keeps the
 * rail at a fixed, small height no matter how many surfaces migrate, killing
 * the vertical-overflow problem the flat console sidebar had.
 */
export const BENTO_NAV_SECTIONS: readonly BentoNavSection[] = [
  { id: "primary", icon: IconHome },
  {
    id: "generativeAi",
    labelKey: "home.sections.generativeAi.label",
    descriptionKey: "home.sections.generativeAi.description",
    icon: IconSparkles,
    areaRoute: ROUTES.BENTO_AREA_GENERATIVE_AI,
  },
  {
    id: "enterpriseSearch",
    labelKey: "home.sections.enterpriseSearch.label",
    descriptionKey: "home.sections.enterpriseSearch.description",
    icon: IconSearch,
    areaRoute: ROUTES.BENTO_AREA_ENTERPRISE_SEARCH,
  },
  {
    id: "management",
    labelKey: "home.sections.management.label",
    descriptionKey: "home.sections.management.description",
    icon: IconSettings,
    areaRoute: ROUTES.BENTO_AREA_MANAGEMENT,
  },
];

export interface BentoNavItem {
  id: string;
  /** i18n key for the item label. */
  titleKey: string;
  /** i18n key for the item description (shown as tile body on hubs + home). */
  descriptionKey: string;
  icon: TablerIcon;
  section: BentoNavSectionId;
  /** Tile tone (gradient) on the mosaic. */
  tone: BentoTone;
  /** Grid span on the mosaic. Defaults to a 1x1 square when omitted. */
  span?: string;
  /**
   * Bento route once this surface is migrated. The command palette links here
   * when present; area hubs still link out to the console for unmigrated ones.
   */
  bentoRoute?: string;
  /**
   * Console route the palette/hub falls back to while a surface is not yet
   * migrated — so `⌘K` and the hubs reach every area during the migration
   * (per ADR 0002, the console stays live until Phase 5).
   */
  consoleRoute: string;
  /** Privilege required to see the item; admins bypass. Undefined = always. */
  privilege?: string;
  /** Hide unless at least one LLM instance is enabled. */
  requiresLlm?: boolean;
}

/**
 * The single source of truth for Bento navigation (T573) — the rail, the three
 * area hubs, the home mosaic, and the `⌘K` palette all read from here. As each
 * Block AG phase migrates a surface, fill in its `bentoRoute` and the palette
 * navigates inside the shell instead of linking out.
 */
export const BENTO_NAV_ITEMS: readonly BentoNavItem[] = [
  // --- primary (rail Home button + palette; not rendered as a hub) ---
  { id: "home", titleKey: "home.title", descriptionKey: "home.welcome", icon: IconHome, section: "primary", tone: "blue", bentoRoute: ROUTES.BENTO_HOME, consoleRoute: ROUTES.HOME },
  { id: "dashboard", titleKey: "dashboard.title", descriptionKey: "dashboard.title", icon: IconLayoutDashboard, section: "primary", tone: "indigo", bentoRoute: ROUTES.BENTO_DASHBOARD, consoleRoute: ROUTES.DASHBOARD },

  // --- generative AI ---
  { id: "aiAgent", titleKey: "home.features.aiAgent.title", descriptionKey: "home.features.aiAgent.description", icon: IconRobot, section: "generativeAi", tone: "blue", span: BENTO_SPAN_FEATURED, bentoRoute: ROUTES.BENTO_AI_AGENT_INSTANCE, consoleRoute: "/admin/ai-agent/instance", privilege: "AI_AGENT_VIEW" },
  { id: "languageModel", titleKey: "home.features.languageModel.title", descriptionKey: "home.features.languageModel.description", icon: IconCpu2, section: "generativeAi", tone: "indigo", span: BENTO_SPAN_WIDE, bentoRoute: ROUTES.BENTO_LLM_INSTANCE, consoleRoute: "/admin/llm/instance", privilege: "LLM_VIEW" },
  { id: "persona", titleKey: "home.features.persona.title", descriptionKey: "home.features.persona.description", icon: IconUserCircle, section: "generativeAi", tone: "rose", span: BENTO_SPAN_WIDE, bentoRoute: ROUTES.BENTO_PERSONA_INSTANCE, consoleRoute: ROUTES.PERSONA_INSTANCE, privilege: "AI_AGENT_VIEW" },
  { id: "embeddingStore", titleKey: "home.features.embeddingStore.title", descriptionKey: "home.features.embeddingStore.description", icon: IconDatabase, section: "generativeAi", tone: "violet", bentoRoute: ROUTES.BENTO_STORE_INSTANCE, consoleRoute: "/admin/store/instance", privilege: "STORE_VIEW" },
  { id: "mcpServer", titleKey: "home.features.mcpServer.title", descriptionKey: "home.features.mcpServer.description", icon: IconServer2, section: "generativeAi", tone: "amber", bentoRoute: ROUTES.BENTO_MCP_INSTANCE, consoleRoute: "/admin/mcp/instance" },
  { id: "customTool", titleKey: "home.features.customTool.title", descriptionKey: "home.features.customTool.description", icon: IconBraces, section: "generativeAi", tone: "slate", bentoRoute: ROUTES.BENTO_CUSTOM_TOOL_INSTANCE, consoleRoute: ROUTES.CUSTOM_TOOL_INSTANCE },
  { id: "routine", titleKey: "home.features.routine.title", descriptionKey: "home.features.routine.description", icon: IconClock, section: "generativeAi", tone: "emerald", bentoRoute: ROUTES.BENTO_ROUTINE_INSTANCE, consoleRoute: ROUTES.ROUTINE_INSTANCE },
  { id: "chatWebhook", titleKey: "home.features.chatWebhook.title", descriptionKey: "home.features.chatWebhook.description", icon: IconWebhook, section: "generativeAi", tone: "rose", bentoRoute: ROUTES.BENTO_CHAT_WEBHOOK_INSTANCE, consoleRoute: ROUTES.CHAT_WEBHOOK_INSTANCE },
  { id: "evalStudio", titleKey: "home.features.evalStudio.title", descriptionKey: "home.features.evalStudio.description", icon: IconFlask2, section: "generativeAi", tone: "amber", bentoRoute: ROUTES.BENTO_EVAL, consoleRoute: ROUTES.BENTO_EVAL, privilege: "AI_AGENT_VIEW" },
  // Cost Governance is intentionally NOT a top-level card here — it lives as a
  // tab inside "AI Analytics & Costs" (aiAnalytics → /cost-governance). A
  // standalone card duplicated the same surface on the Generative AI hub.
  { id: "skills", titleKey: "home.features.skills.title", descriptionKey: "home.features.skills.description", icon: IconSparkles, section: "generativeAi", tone: "violet", bentoRoute: ROUTES.BENTO_SKILL, consoleRoute: ROUTES.SKILL_ROOT },
  { id: "aiAnalytics", titleKey: "home.features.aiAnalytics.title", descriptionKey: "home.features.aiAnalytics.description", icon: IconReportAnalytics, section: "generativeAi", tone: "indigo", bentoRoute: ROUTES.BENTO_AI_ANALYTICS, consoleRoute: ROUTES.AI_ANALYTICS_ROOT },
  { id: "gateway", titleKey: "gateway.title", descriptionKey: "gateway.description", icon: IconApi, section: "generativeAi", tone: "indigo", bentoRoute: ROUTES.BENTO_GATEWAY, consoleRoute: ROUTES.BENTO_GATEWAY, privilege: "LLM_VIEW" },
  // Chat is intentionally NOT a top-level card here — you enter chat from a
  // specific surface: the AI Agent card (opens `/bento/chat/agent/:id`) or the
  // Persona card (`/bento/chat/persona/:id`), and the home overview keeps its
  // inline ChatStarter. A standalone "Chat" tile just duplicated that entry.
  // The `/bento/chat` route (default agent) still works for deep links.

  // --- enterprise search ---
  { id: "semanticNavigation", titleKey: "home.features.semanticNavigation.title", descriptionKey: "home.features.semanticNavigation.description", icon: IconCompass, section: "enterpriseSearch", tone: "emerald", span: BENTO_SPAN_FEATURED, bentoRoute: ROUTES.BENTO_SN_INSTANCE, consoleRoute: "/admin/sn/instance", privilege: "SN_VIEW" },
  { id: "searchEngine", titleKey: "home.features.searchEngine.title", descriptionKey: "home.features.searchEngine.description", icon: IconZoomCode, section: "enterpriseSearch", tone: "emerald", span: BENTO_SPAN_WIDE, bentoRoute: ROUTES.BENTO_SE_INSTANCE, consoleRoute: "/admin/se/instance", privilege: "SE_VIEW" },
  { id: "integration", titleKey: "home.features.integration.title", descriptionKey: "home.features.integration.description", icon: IconPlugConnectedX, section: "enterpriseSearch", tone: "amber", bentoRoute: ROUTES.BENTO_INTEGRATION_INSTANCE, consoleRoute: "/admin/integration/instance" },
  { id: "graphqlExplorer", titleKey: "home.features.graphqlExplorer.title", descriptionKey: "home.features.graphqlExplorer.description", icon: IconBrandGraphql, section: "enterpriseSearch", tone: "rose", bentoRoute: ROUTES.BENTO_GRAPHQL, consoleRoute: ROUTES.GRAPHQL_ROOT },
  { id: "thesaurus", titleKey: "thesaurus.title", descriptionKey: "thesaurus.description", icon: IconSitemap, section: "enterpriseSearch", tone: "violet", bentoRoute: ROUTES.BENTO_THESAURUS, consoleRoute: ROUTES.BENTO_THESAURUS, privilege: "SN_VIEW" },

  // --- management ---
  { id: "adminUsers", titleKey: "admin.users.title", descriptionKey: "admin.users.description", icon: IconUsers, section: "management", tone: "slate", bentoRoute: ROUTES.BENTO_ADMIN_USERS, consoleRoute: ROUTES.BENTO_ADMIN_USERS, privilege: "__admin__" },
  { id: "adminGroups", titleKey: "admin.groups.title", descriptionKey: "admin.groups.description", icon: IconUsersGroup, section: "management", tone: "slate", bentoRoute: ROUTES.BENTO_ADMIN_GROUPS, consoleRoute: ROUTES.BENTO_ADMIN_GROUPS, privilege: "__admin__" },
  { id: "adminRoles", titleKey: "admin.roles.title", descriptionKey: "admin.roles.description", icon: IconUserShield, section: "management", tone: "slate", bentoRoute: ROUTES.BENTO_ADMIN_ROLES, consoleRoute: ROUTES.BENTO_ADMIN_ROLES, privilege: "__admin__" },
  { id: "assets", titleKey: "home.features.assets.title", descriptionKey: "home.features.assets.description", icon: IconFolder, section: "management", tone: "amber", bentoRoute: ROUTES.BENTO_ASSET, consoleRoute: ROUTES.ASSET_ROOT },
  { id: "pages", titleKey: "home.features.pages.title", descriptionKey: "home.features.pages.description", icon: IconGlobe, section: "management", tone: "indigo", bentoRoute: ROUTES.BENTO_PAGE, consoleRoute: ROUTES.PAGE_ROOT },
  { id: "git", titleKey: "home.features.git.title", descriptionKey: "home.features.git.description", icon: IconGitBranch, section: "management", tone: "violet", bentoRoute: ROUTES.BENTO_GIT, consoleRoute: ROUTES.GIT_ROOT },
  { id: "apiToken", titleKey: "apiToken.title", descriptionKey: "apiToken.description", icon: IconCode, section: "management", tone: "slate", bentoRoute: ROUTES.BENTO_TOKEN_INSTANCE, consoleRoute: ROUTES.ADMIN_TOKENS },
  { id: "marketplace", titleKey: "home.features.marketplace.title", descriptionKey: "home.features.marketplace.description", icon: IconBuildingStore, section: "management", tone: "blue", bentoRoute: ROUTES.BENTO_MARKETPLACE, consoleRoute: ROUTES.MARKETPLACE_ROOT },
  { id: "import", titleKey: "home.features.import.title", descriptionKey: "home.features.import.description", icon: IconFileImport, section: "management", tone: "indigo", bentoRoute: ROUTES.BENTO_IMPORT, consoleRoute: ROUTES.EXCHANGE_IMPORT },
  { id: "logging", titleKey: "home.features.logging.title", descriptionKey: "home.features.logging.description", icon: IconReceiptRupee, section: "management", tone: "rose", bentoRoute: ROUTES.BENTO_LOGGING, consoleRoute: ROUTES.LOGGING_INSTANCE },
  { id: "globalSettings", titleKey: "globalSettings.breadcrumb", descriptionKey: "globalSettings.description", icon: IconSettings, section: "management", tone: "slate", bentoRoute: ROUTES.BENTO_GLOBAL_SETTINGS, consoleRoute: ROUTES.GLOBAL_SETTINGS, privilege: "__admin__" },
  { id: "systemInfo", titleKey: "systemInfo.title", descriptionKey: "systemInfo.description", icon: IconInfoCircle, section: "management", tone: "slate", bentoRoute: ROUTES.BENTO_SYSTEM_INFO, consoleRoute: ROUTES.ADMIN_SYSTEM_INFO, privilege: "__admin__" },
];

/**
 * Privilege-filtered nav items for the current user (admins see all). The
 * synthetic `__admin__` privilege gates admin-only areas. Feature-flag gating
 * (storage/git/marketplace) is intentionally deferred to the target route
 * itself — the palette/hub links out to the console for unmigrated areas,
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

export interface BentoNavGroup {
  section: BentoNavSection;
  items: BentoNavItem[];
}

/**
 * Visible nav items grouped by section, in section order, dropping empty
 * groups. The home mosaic, the area hubs, and the rail all derive from this.
 */
export function useVisibleBentoSections(): BentoNavGroup[] {
  const items = useVisibleBentoNav();
  return useMemo(
    () =>
      BENTO_NAV_SECTIONS.map((section) => ({
        section,
        items: items.filter((i) => i.section === section.id),
      })).filter((g) => g.items.length > 0),
    [items],
  );
}

/** Resolve where an item navigates: bento route if migrated, else console. */
export function bentoNavTarget(item: BentoNavItem): string {
  return item.bentoRoute ?? item.consoleRoute;
}

/** Look up a section by its hub route (used by the area hub page). */
export function bentoSectionByAreaRoute(areaRoute: string): BentoNavSection | undefined {
  return BENTO_NAV_SECTIONS.find((s) => s.areaRoute === areaRoute);
}

/** The hub route for an item's section, if the section has one. */
export function bentoSectionAreaRoute(item: BentoNavItem): string | undefined {
  return BENTO_NAV_SECTIONS.find((s) => s.id === item.section)?.areaRoute;
}
