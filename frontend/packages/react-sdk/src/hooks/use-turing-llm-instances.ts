import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  fetchAgentContextInfo,
  fetchLlmContextInfo,
  fetchLlmInstances,
} from "../core/api";
import type { TurLlmInstance } from "../core/types";

export interface UseTuringLlmInstancesOptions {
  /**
   * Optional {@code localStorage} key used to persist the selected instance
   * across reloads. Omit to keep the selection in-memory only — the public
   * SDK default.
   */
  readonly persistKey?: string;
  /**
   * Context-window fallback when neither the provider probe nor the admin's
   * configured value yield a usable number. Defaults to {@code 128000} —
   * a conservative GPT-4o-class baseline that matches the admin chat code.
   */
  readonly defaultContextWindow?: number;
  /**
   * Agent id to scope the context-window probe through. When set, the hook
   * calls {@code GET /v2/ai-agent/{agentId}/chat/context-info} (which can
   * carry an agent-specific override); otherwise it hits the plain
   * {@code GET /v2/llm/{id}/chat/context-info} endpoint.
   */
  readonly agentId?: string;
  /**
   * Disables the initial listing fetch when {@code false}. Useful when the
   * host gates the hook on a parent auth check. Defaults to {@code true}.
   */
  readonly enabled?: boolean;
  /**
   * Hide instances with {@code enabled === 0}. The admin chat ships with
   * {@code true} since the picker should only show usable models.
   */
  readonly filterDisabled?: boolean;
}

export interface UseTuringLlmInstancesReturn {
  /** All instances returned by the backend, filtered per {@link UseTuringLlmInstancesOptions#filterDisabled}. */
  readonly instances: TurLlmInstance[];
  /** {@code true} once the initial listing fetch has resolved (success or failure). */
  readonly loaded: boolean;
  /** Id of the currently picked instance, or empty string when none/unloaded. */
  readonly selectedId: string;
  /** Resolved instance object matching {@link selectedId}, or {@code undefined}. */
  readonly selectedInstance: TurLlmInstance | undefined;
  /**
   * Effective context-window for the picked instance: provider probe → admin
   * config on the entity → {@link UseTuringLlmInstancesOptions#defaultContextWindow}.
   */
  readonly contextWindow: number;
  /** Imperatively change the selected instance (and persist if {@code persistKey} is set). */
  readonly select: (id: string) => void;
  /** Last error string from the listing fetch, or {@code null}. */
  readonly error: string | null;
}

const DEFAULT_CONTEXT_WINDOW = 128000;

const contextWindowCache = new Map<string, number>();

function readPersisted(key: string | undefined): string {
  if (!key) return "";
  try {
    return globalThis.localStorage?.getItem(key) ?? "";
  } catch {
    return "";
  }
}

function writePersisted(key: string | undefined, value: string): void {
  if (!key) return;
  try {
    globalThis.localStorage?.setItem(key, value);
  } catch {
    // localStorage may be unavailable (SSR, private mode, quota)
  }
}

/**
 * Lists the LLM instances visible to the current session and tracks the
 * picked one. Wraps {@link fetchLlmInstances} + {@link fetchLlmContextInfo}
 * (or {@link fetchAgentContextInfo} when {@code agentId} is set) and adds:
 *
 * <ul>
 *   <li>Optional {@code localStorage} selection persistence.
 *   <li>A module-level {@code Map} cache for context-window probes so a
 *       repeated mount or re-select doesn't re-hit the backend.
 *   <li>Auto-pick the first enabled instance when the persisted id is gone
 *       (admin had a stale id pointing at a deleted instance otherwise).
 * </ul>
 *
 * <p>Drop-in replacement for the admin chat's local {@code useLlmInstances}
 * hook ({@code frontend/apps/turing-app/src/app/console/chat/hooks/use-llm-instances.ts})
 * once T226 lands.
 *
 * @since 2026.3.1
 */
export function useTuringLlmInstances(
  options: UseTuringLlmInstancesOptions = {},
): UseTuringLlmInstancesReturn {
  const {
    persistKey,
    defaultContextWindow = DEFAULT_CONTEXT_WINDOW,
    agentId,
    enabled = true,
    filterDisabled = true,
  } = options;

  const [instances, setInstances] = useState<TurLlmInstance[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [selectedId, setSelectedId] = useState<string>(() => readPersisted(persistKey));
  const [fetchedContextWindow, setFetchedContextWindow] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const persistKeyRef = useRef(persistKey);
  persistKeyRef.current = persistKey;

  useEffect(() => {
    if (!enabled) return;
    let alive = true;
    fetchLlmInstances()
      .then((all) => {
        if (!alive) return;
        const filtered = filterDisabled ? all.filter((i) => i.enabled === 1) : all;
        setInstances(filtered);
        if (filtered.length > 0) {
          const stored = readPersisted(persistKeyRef.current);
          const valid = stored && filtered.some((i) => i.id === stored);
          if (!valid) {
            setSelectedId(filtered[0].id);
            writePersisted(persistKeyRef.current, filtered[0].id);
          }
        }
      })
      .catch((err: unknown) => {
        if (!alive) return;
        setError(err instanceof Error ? err.message : "Failed to fetch LLM instances");
      })
      .finally(() => {
        if (alive) setLoaded(true);
      });
    return () => {
      alive = false;
    };
  }, [enabled, filterDisabled]);

  // Probe the picked instance for its live context window. Bails when the id
  // is empty / unknown to the loaded list (e.g. a stale persisted id pointing
  // at a deleted instance returns 400 from the backend).
  useEffect(() => {
    if (!selectedId) {
      setFetchedContextWindow(null);
      return;
    }
    if (!loaded) return;
    if (!instances.some((i) => i.id === selectedId)) {
      setFetchedContextWindow(null);
      return;
    }
    const cached = contextWindowCache.get(cacheKey(agentId, selectedId));
    if (cached) {
      setFetchedContextWindow(cached);
      return;
    }
    let alive = true;
    const probe = agentId
      ? fetchAgentContextInfo(agentId, selectedId)
      : fetchLlmContextInfo(selectedId);
    probe
      .then((info) => {
        if (!alive) return;
        if (info.contextWindow > 0) {
          contextWindowCache.set(cacheKey(agentId, selectedId), info.contextWindow);
          setFetchedContextWindow(info.contextWindow);
        }
      })
      .catch(() => {
        // Silent fallback to the configured value on the entity. The hook
        // already widens through the `selectedInstance.contextWindow` path
        // below, so a failed probe never bricks the picker.
        if (alive) setFetchedContextWindow(null);
      });
    return () => {
      alive = false;
    };
  }, [agentId, selectedId, loaded, instances]);

  const select = useCallback((id: string) => {
    setSelectedId(id);
    writePersisted(persistKeyRef.current, id);
  }, []);

  const selectedInstance = useMemo(
    () => instances.find((i) => i.id === selectedId),
    [instances, selectedId],
  );

  const contextWindow =
    fetchedContextWindow ??
    selectedInstance?.contextWindow ??
    defaultContextWindow;

  return {
    instances,
    loaded,
    selectedId,
    selectedInstance,
    contextWindow,
    select,
    error,
  };
}

function cacheKey(agentId: string | undefined, llmId: string): string {
  return agentId ? `${agentId}:${llmId}` : llmId;
}
