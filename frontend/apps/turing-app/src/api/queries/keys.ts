/**
 * Centralized React Query keys. Mutations invalidate by referencing these
 * helpers so we never accidentally invalidate the wrong cache slice.
 *
 * Convention: nested objects mirror the resource hierarchy. Each leaf is a
 * factory returning a deterministic array (React Query compares by deep
 * equality on the array contents).
 */
export const queryKeys = {
  tenants: {
    all: () => ['tenants'] as const,
    mine: () => [...queryKeys.tenants.all(), 'mine'] as const,
    adminList: () => [...queryKeys.tenants.all(), 'admin', 'list'] as const,
  },
  embeddingModels: {
    all: () => ['embedding-models'] as const,
    list: () => [...queryKeys.embeddingModels.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.embeddingModels.all(), 'detail', id] as const,
  },
  skills: {
    all: () => ['skills'] as const,
    list: () => [...queryKeys.skills.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.skills.all(), 'detail', id] as const,
    files: (id: string) => [...queryKeys.skills.all(), 'files', id] as const,
    file: (id: string, path: string) => [...queryKeys.skills.all(), 'file', id, path] as const,
  },
  snSites: {
    all: () => ['sn-sites'] as const,
    list: () => [...queryKeys.snSites.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.snSites.all(), 'detail', id] as const,
  },
  locales: {
    all: () => ['locales'] as const,
    list: () => [...queryKeys.locales.all(), 'list'] as const,
  },
  snSiteLocales: {
    all: () => ['sn-site-locales'] as const,
    listBySite: (siteId: string) => [...queryKeys.snSiteLocales.all(), 'list', 'site', siteId] as const,
  },
  llmInstances: {
    all: () => ['llm-instances'] as const,
    list: () => [...queryKeys.llmInstances.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.llmInstances.all(), 'detail', id] as const,
  },
  aiAgents: {
    all: () => ['ai-agents'] as const,
    list: () => [...queryKeys.aiAgents.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.aiAgents.all(), 'detail', id] as const,
    systemPromptPreview: (id: string, flowId?: string) =>
      [...queryKeys.aiAgents.all(), 'system-prompt', 'preview', id, flowId ?? 'default'] as const,
    systemPromptValidate: (id: string) =>
      [...queryKeys.aiAgents.all(), 'system-prompt', 'validate', id] as const,
  },
  personas: {
    all: () => ['personas'] as const,
    list: () => [...queryKeys.personas.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.personas.all(), 'detail', id] as const,
  },
  seInstances: {
    all: () => ['se-instances'] as const,
    list: () => [...queryKeys.seInstances.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.seInstances.all(), 'detail', id] as const,
  },
  seCores: {
    all: () => ['se-cores'] as const,
    listByInstance: (seId: string) => [...queryKeys.seCores.all(), 'list', 'instance', seId] as const,
  },
  features: {
    all: () => ['features'] as const,
    current: () => [...queryKeys.features.all(), 'current'] as const,
  },
  chatFlows: {
    all: () => ['chat-flows'] as const,
    listByAgent: (agentId: string) => [...queryKeys.chatFlows.all(), 'list', 'agent', agentId] as const,
    detail: (id: string) => [...queryKeys.chatFlows.all(), 'detail', id] as const,
    triggerConflicts: (agentId: string) =>
      [...queryKeys.chatFlows.all(), 'trigger-conflicts', agentId] as const,
    lint: (agentId: string, flowId: string) =>
      [...queryKeys.chatFlows.all(), 'lint', agentId, flowId] as const,
    funnel: (agentId: string, flowId: string) =>
      [...queryKeys.chatFlows.all(), 'funnel', agentId, flowId] as const,
  },
  agentEval: {
    all: () => ['agent-eval'] as const,
    sets: (agentId: string) => [...queryKeys.agentEval.all(), 'sets', agentId] as const,
    gate: (agentId: string) => [...queryKeys.agentEval.all(), 'gate', agentId] as const,
    report: (agentId: string) => [...queryKeys.agentEval.all(), 'report', agentId] as const,
    available: (agentId: string) => [...queryKeys.agentEval.all(), 'available', agentId] as const,
  },
  chatFlowRecipes: {
    all: () => ['chat-flow-recipes'] as const,
    list: () => [...queryKeys.chatFlowRecipes.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.chatFlowRecipes.all(), 'detail', id] as const,
  },
  aiAgentSlots: {
    all: () => ['ai-agent-slots'] as const,
    listByAgent: (agentId: string) => [...queryKeys.aiAgentSlots.all(), 'list', 'agent', agentId] as const,
    detail: (id: string) => [...queryKeys.aiAgentSlots.all(), 'detail', id] as const,
  },
  customTools: {
    all: () => ['custom-tools'] as const,
    list: () => [...queryKeys.customTools.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.customTools.all(), 'detail', id] as const,
    descriptor: () => [...queryKeys.customTools.all(), 'descriptor'] as const,
    draft: (id: string) => [...queryKeys.customTools.all(), 'draft', id] as const,
  },
  routines: {
    all: () => ['routines'] as const,
    list: () => [...queryKeys.routines.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.routines.all(), 'detail', id] as const,
  },
  chatWebhooks: {
    all: () => ['chat-webhooks'] as const,
    list: () => [...queryKeys.chatWebhooks.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.chatWebhooks.all(), 'detail', id] as const,
  },
  intents: {
    all: () => ['intents'] as const,
    listByAgent: (agentId: string) => [...queryKeys.intents.all(), 'list', 'agent', agentId] as const,
    detail: (id: string) => [...queryKeys.intents.all(), 'detail', id] as const,
  },
  analyticsIntents: {
    all: () => ['analytics-intents'] as const,
    listByAgent: (agentId: string) => [...queryKeys.analyticsIntents.all(), 'list', 'agent', agentId] as const,
    detail: (id: string) => [...queryKeys.analyticsIntents.all(), 'detail', id] as const,
  },
  mcpServers: {
    all: () => ['mcp-servers'] as const,
    list: () => [...queryKeys.mcpServers.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.mcpServers.all(), 'detail', id] as const,
  },
  integrationInstances: {
    all: () => ['integration-instances'] as const,
    list: () => [...queryKeys.integrationInstances.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.integrationInstances.all(), 'detail', id] as const,
  },
  storeInstances: {
    all: () => ['store-instances'] as const,
    list: () => [...queryKeys.storeInstances.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.storeInstances.all(), 'detail', id] as const,
  },
  storeCollections: {
    all: () => ['store-collections'] as const,
    listByInstance: (storeId: string) => [...queryKeys.storeCollections.all(), 'list', 'instance', storeId] as const,
    documentCount: (storeId: string, collectionName: string) =>
      [...queryKeys.storeCollections.all(), 'document-count', storeId, collectionName] as const,
  },
  tokenInstances: {
    all: () => ['token-instances'] as const,
    list: () => [...queryKeys.tokenInstances.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.tokenInstances.all(), 'detail', id] as const,
  },
  marketplaceItems: {
    all: () => ['marketplace-items'] as const,
    list: () => [...queryKeys.marketplaceItems.all(), 'list'] as const,
  },
  authDiscovery: {
    all: () => ['auth-discovery'] as const,
    current: () => [...queryKeys.authDiscovery.all(), 'current'] as const,
  },
  adminUsers: {
    all: () => ['admin-users'] as const,
    list: (source: 'jpa' | 'keycloak') => [...queryKeys.adminUsers.all(), 'list', source] as const,
  },
  adminGroups: {
    all: () => ['admin-groups'] as const,
    list: (source: 'jpa' | 'keycloak') => [...queryKeys.adminGroups.all(), 'list', source] as const,
  },
  adminRoles: {
    all: () => ['admin-roles'] as const,
    list: () => [...queryKeys.adminRoles.all(), 'list'] as const,
  },
  chatSessions: {
    all: () => ['chat-sessions'] as const,
    slots: (conversationId: string) =>
      [...queryKeys.chatSessions.all(), 'slots', conversationId] as const,
    slotAudit: (conversationId: string) =>
      [...queryKeys.chatSessions.all(), 'slot-audit', conversationId] as const,
  },
  chatAnalytics: {
    all: () => ['chat-analytics'] as const,
    health: () => [...queryKeys.chatAnalytics.all(), 'health'] as const,
    sessions: (params: Record<string, unknown>) =>
      [...queryKeys.chatAnalytics.all(), 'sessions', params] as const,
    session: (id: string) => [...queryKeys.chatAnalytics.all(), 'session', id] as const,
    transcript: (id: string) => [...queryKeys.chatAnalytics.all(), 'transcript', id] as const,
    timeseries: (params: Record<string, unknown>) =>
      [...queryKeys.chatAnalytics.all(), 'timeseries', params] as const,
    scorecard: (params: Record<string, unknown>) =>
      [...queryKeys.chatAnalytics.all(), 'scorecard', params] as const,
    toolLatency: (params: Record<string, unknown>) =>
      [...queryKeys.chatAnalytics.all(), 'tool-latency', params] as const,
    routerDecisions: (conversationId: string) =>
      [...queryKeys.chatAnalytics.all(), 'router-decisions', conversationId] as const,
    slotSseChannels: () => [...queryKeys.chatAnalytics.all(), 'slot-sse-channels'] as const,
  },
  parkedConversations: {
    all: () => ['parked-conversations'] as const,
    list: () => [...queryKeys.parkedConversations.all(), 'list'] as const,
  },
} as const;
