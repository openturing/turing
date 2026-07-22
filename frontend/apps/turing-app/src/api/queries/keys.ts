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
  // T624 — ONNX-verified HuggingFace embedding models, keyed by the search query.
  huggingFaceModels: {
    all: () => ['huggingface-models'] as const,
    list: (query: string) => [...queryKeys.huggingFaceModels.all(), 'list', query] as const,
    // T627 — embedding-dimension check for a picked repo vs. the default.
    dimensionCheck: (repoId: string) =>
      [...queryKeys.huggingFaceModels.all(), 'dimension-check', repoId] as const,
    // T628 — ONNX artifact variants a repo ships.
    variants: (repoId: string) =>
      [...queryKeys.huggingFaceModels.all(), 'variants', repoId] as const,
  },
  // T625 — embedding-capable models per LLM vendor for the embedding form picker.
  // Same key discipline as llmModels: the key never stores the secret.
  llmEmbeddingModels: {
    all: () => ['llm-embedding-models'] as const,
    list: (params: { vendorId: string; instanceId?: string; url?: string; hasKey: boolean }) =>
      [...queryKeys.llmEmbeddingModels.all(), params] as const,
  },
  skills: {
    all: () => ['skills'] as const,
    list: () => [...queryKeys.skills.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.skills.all(), 'detail', id] as const,
    files: (id: string) => [...queryKeys.skills.all(), 'files', id] as const,
    file: (id: string, path: string) => [...queryKeys.skills.all(), 'file', id, path] as const,
    uiComponents: (id: string) => [...queryKeys.skills.all(), 'ui-components', id] as const,
  },
  snSites: {
    all: () => ['sn-sites'] as const,
    list: () => [...queryKeys.snSites.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.snSites.all(), 'detail', id] as const,
  },
  snSiteFieldCoverage: {
    all: () => ['sn-site-field-coverage'] as const,
    bySite: (siteId: string) => [...queryKeys.snSiteFieldCoverage.all(), 'site', siteId] as const,
  },
  snSiteContentFit: {
    all: () => ['sn-site-content-fit'] as const,
    bySite: (siteId: string) => [...queryKeys.snSiteContentFit.all(), 'site', siteId] as const,
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
    deprecations: () => [...queryKeys.llmInstances.all(), 'deprecations'] as const,
    changeFeed: () => [...queryKeys.llmInstances.all(), 'change-feed'] as const,
  },
  llmInstanceCapabilities: {
    all: () => ['llm-instance-capabilities'] as const,
    list: (instanceId: string) =>
      [...queryKeys.llmInstanceCapabilities.all(), 'list', instanceId] as const,
  },
  // T577 — selectable models per vendor for the model picker. Keyed by vendor +
  // instance + endpoint + whether a key is present (never the key value itself),
  // so toggling any of those refetches without caching secrets.
  llmModels: {
    all: () => ['llm-models'] as const,
    list: (params: { vendorId: string; instanceId?: string; url?: string; hasKey: boolean }) =>
      [...queryKeys.llmModels.all(), params] as const,
  },
  aiAgents: {
    all: () => ['ai-agents'] as const,
    list: () => [...queryKeys.aiAgents.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.aiAgents.all(), 'detail', id] as const,
    systemPromptPreview: (id: string, flowId?: string, nodeId?: string, vars?: string) =>
      [...queryKeys.aiAgents.all(), 'system-prompt', 'preview', id,
        flowId ?? 'default', nodeId ?? 'entry', vars ?? 'empty'] as const,
    // T612/T618 — replay the assembled prompt from a real conversation; the
    // optional turnIndex selects a captured past turn (verbatim) vs current state.
    systemPromptReplay: (id: string, conversationId: string, turnIndex?: number) =>
      [...queryKeys.aiAgents.all(), 'system-prompt', 'replay', id, conversationId,
        turnIndex ?? 'current'] as const,
    systemPromptValidate: (id: string) =>
      [...queryKeys.aiAgents.all(), 'system-prompt', 'validate', id] as const,
  },
  personas: {
    all: () => ['personas'] as const,
    list: () => [...queryKeys.personas.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.personas.all(), 'detail', id] as const,
  },
  // Block AT (T701) — Persona Match: N×N persona↔content fit projects.
  personaMatch: {
    all: () => ['persona-match'] as const,
    list: () => [...queryKeys.personaMatch.all(), 'list'] as const,
    detail: (id: string) =>
      [...queryKeys.personaMatch.all(), 'detail', id] as const,
    matrix: (id: string) =>
      [...queryKeys.personaMatch.all(), 'matrix', id] as const,
  },
  // Block AU (T706) — Persona Dialogue: saved multi-project dialogues.
  personaDialogue: {
    all: () => ['persona-dialogue'] as const,
    list: () => [...queryKeys.personaDialogue.all(), 'list'] as const,
    detail: (id: string) =>
      [...queryKeys.personaDialogue.all(), 'detail', id] as const,
  },
  // Block AW (T730) — Synthetic User Research: cohort interview studies.
  personaResearch: {
    all: () => ['research-study'] as const,
    list: () => [...queryKeys.personaResearch.all(), 'list'] as const,
    detail: (id: string) =>
      [...queryKeys.personaResearch.all(), 'detail', id] as const,
    insights: (id: string) =>
      [...queryKeys.personaResearch.all(), 'insights', id] as const,
    saturation: (id: string) =>
      [...queryKeys.personaResearch.all(), 'saturation', id] as const,
    graph: (id: string) =>
      [...queryKeys.personaResearch.all(), 'graph', id] as const,
    drift: (id: string) =>
      [...queryKeys.personaResearch.all(), 'drift', id] as const,
    conceptFit: (id: string) =>
      [...queryKeys.personaResearch.all(), 'concept-fit', id] as const,
  },
  // T447 — self-tuning suggestions, scoped per agent.
  agentSuggestions: {
    all: () => ['agent-suggestions'] as const,
    list: (agentId: string) =>
      [...queryKeys.agentSuggestions.all(), 'list', agentId] as const,
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
  evalStudio: {
    all: () => ['eval-studio'] as const,
    datasets: () => [...queryKeys.evalStudio.all(), 'datasets'] as const,
    graderStacks: () => [...queryKeys.evalStudio.all(), 'grader-stacks'] as const,
    // T593 — per-agent human-review inbox.
    reviewInbox: (agentId: string) =>
      [...queryKeys.evalStudio.all(), 'review-inbox', agentId] as const,
    // T594 — per-agent inter-annotator agreement + MODEL-vs-human calibration.
    agreement: (agentId: string) =>
      [...queryKeys.evalStudio.all(), 'agreement', agentId] as const,
    calibration: (agentId: string) =>
      [...queryKeys.evalStudio.all(), 'calibration', agentId] as const,
    // T599 — per-agent eval run history (timeline + drill-down).
    runHistory: (agentId: string) =>
      [...queryKeys.evalStudio.all(), 'run-history', agentId] as const,
    // T603 — per-agent continuous / online-eval snapshots (drift timeline).
    onlineHistory: (agentId: string) =>
      [...queryKeys.evalStudio.all(), 'online-history', agentId] as const,
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
  // Block AZ (§XLIX) — Governed LLM Gateway virtual keys + per-key spend.
  gateway: {
    all: () => ['gateway'] as const,
    keys: () => [...queryKeys.gateway.all(), 'keys'] as const,
    usage: () => [...queryKeys.gateway.all(), 'usage'] as const,
  },
  // Block AQ (§XL) — Thesaurus (Knowledge Base / microthesauri / terms) admin.
  thesaurus: {
    all: () => ['thesaurus'] as const,
    kbList: () => [...queryKeys.thesaurus.all(), 'kb', 'list'] as const,
    kbDetail: (id: string) => [...queryKeys.thesaurus.all(), 'kb', 'detail', id] as const,
    microList: (kbId: string) =>
      [...queryKeys.thesaurus.all(), 'micro', 'list', kbId] as const,
    microDetail: (kbId: string, id: string) =>
      [...queryKeys.thesaurus.all(), 'micro', 'detail', kbId, id] as const,
    termList: (microId: string) =>
      [...queryKeys.thesaurus.all(), 'term', 'list', microId] as const,
    seeds: () => [...queryKeys.thesaurus.all(), 'seeds'] as const,
    siteSelections: (snSiteId: string) =>
      [...queryKeys.thesaurus.all(), 'site', 'selections', snSiteId] as const,
    siteConfig: (snSiteId: string) =>
      [...queryKeys.thesaurus.all(), 'site', 'config', snSiteId] as const,
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
  // T575 — customizable Bento list layout, scoped per list surface.
  bentoLayout: {
    all: () => ['bento-layout'] as const,
    byList: (listId: string) => [...queryKeys.bentoLayout.all(), listId] as const,
  },
  // T662–T666 (Block AP) — engine-agnostic synonyms per SN site.
  synonyms: {
    all: () => ['synonyms'] as const,
    list: (siteId: string) => [...queryKeys.synonyms.all(), 'list', siteId] as const,
    detail: (siteId: string, id: string) =>
      [...queryKeys.synonyms.all(), 'detail', siteId, id] as const,
    support: (siteId: string) => [...queryKeys.synonyms.all(), 'support', siteId] as const,
  },
} as const;
