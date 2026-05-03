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
} as const;
