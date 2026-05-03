/**
 * Centralized React Query keys. Mutations invalidate by referencing these
 * helpers so we never accidentally invalidate the wrong cache slice.
 *
 * Convention: nested objects mirror the resource hierarchy. Each leaf is a
 * factory returning a deterministic array (React Query compares by deep
 * equality on the array contents).
 */
export const queryKeys = {
  embeddingModels: {
    all: () => ['embedding-models'] as const,
    list: () => [...queryKeys.embeddingModels.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.embeddingModels.all(), 'detail', id] as const,
  },
  snSites: {
    all: () => ['sn-sites'] as const,
    list: () => [...queryKeys.snSites.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.snSites.all(), 'detail', id] as const,
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
  },
  seInstances: {
    all: () => ['se-instances'] as const,
    list: () => [...queryKeys.seInstances.all(), 'list'] as const,
    detail: (id: string) => [...queryKeys.seInstances.all(), 'detail', id] as const,
  },
  features: {
    all: () => ['features'] as const,
    current: () => [...queryKeys.features.all(), 'current'] as const,
  },
} as const;
