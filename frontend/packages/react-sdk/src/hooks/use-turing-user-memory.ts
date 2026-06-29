import { useCallback, useEffect, useMemo, useState } from "react";
import axios from "axios";
import type { ClientToolHandler } from "../core/types";
import { useOptionalTuringContext } from "../core/use-turing-context";

/**
 * T446 / §XXIII.5 — cross-conversation personal memory.
 *
 * <p>Holds the host-supplied stable {@code userId} and wires the two built-in
 * memory client tools (advertised by the backend when the agent has
 * {@code userMemoryEnabled}) to the {@code /sn/{site}/user-memory} REST endpoints:
 *
 * <ul>
 *   <li>{@code recall_user_memory} → GET the user's remembered facts and return
 *       them to the agent;</li>
 *   <li>{@code remember_fact} → POST/upsert a {key,value} fact.</li>
 * </ul>
 *
 * <p>Spread {@link UseTuringUserMemoryReturn.clientTools} into {@code useTuringChat}.
 * The same hook exposes {@code memories}/{@code refresh}/{@code remove}/{@code clear}
 * for a "what the assistant remembers about me" panel (GDPR delete). Because the
 * SDK owns the userId, the server never threads it through the chat path.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export interface TuringUserMemory {
  id: string;
  userId: string;
  key: string;
  content: string;
  updatedAt: number;
}

export interface UseTuringUserMemoryOptions {
  /** Stable per-user id (e.g. a logged-in email or opaque token). Required. */
  readonly userId: string;
  /** Disable network + handlers (e.g. before the user is known). Defaults to true. */
  readonly enabled?: boolean;
}

export interface UseTuringUserMemoryReturn {
  /** Spread into {@code useTuringChat}'s {@code clientTools} option. */
  clientTools: Record<string, ClientToolHandler>;
  /** The user's remembered facts (for the panel). */
  memories: TuringUserMemory[];
  /** Re-fetch the list. */
  refresh: () => void;
  /** Delete one remembered fact. */
  remove: (id: string) => Promise<void>;
  /** Delete every remembered fact for this user (GDPR "forget me"). */
  clear: () => Promise<void>;
}

export function useTuringUserMemory(
  options: UseTuringUserMemoryOptions,
): UseTuringUserMemoryReturn {
  const ctx = useOptionalTuringContext();
  if (!ctx) {
    throw new Error("useTuringUserMemory requires a <TuringProvider> ancestor.");
  }
  const siteName = ctx.config.site;
  const { userId, enabled = true } = options;
  const base = `/sn/${siteName}/user-memory`;

  const [memories, setMemories] = useState<TuringUserMemory[]>([]);
  const [tick, setTick] = useState(0);

  const refresh = useCallback(() => setTick((t) => t + 1), []);

  useEffect(() => {
    if (!enabled || !userId) return;
    let alive = true;
    axios
      .get<TuringUserMemory[]>(base, { params: { userId } })
      .then((res) => {
        if (alive) setMemories(res.data ?? []);
      })
      .catch(() => {
        /* panel stays empty on error — non-fatal */
      });
    return () => {
      alive = false;
    };
  }, [enabled, userId, base, tick]);

  const remove = useCallback(
    async (id: string) => {
      await axios.delete(`${base}/${encodeURIComponent(id)}`);
      setMemories((prev) => prev.filter((m) => m.id !== id));
    },
    [base],
  );

  const clear = useCallback(async () => {
    await axios.delete(base, { params: { userId } });
    setMemories([]);
  }, [base, userId]);

  const clientTools = useMemo<Record<string, ClientToolHandler>>(
    () => ({
      recall_user_memory: async () => {
        if (!userId) return { facts: [] };
        const res = await axios.get<TuringUserMemory[]>(base, { params: { userId } });
        const facts = res.data ?? [];
        setMemories(facts);
        return facts.length === 0
          ? { facts: [], note: "Nothing remembered about this user yet." }
          : { facts: facts.map((f) => ({ key: f.key, value: f.content })) };
      },
      remember_fact: async (args) => {
        const { key, value } = (args as { key?: string; value?: string }) ?? {};
        if (!userId || !key) return { success: false, error: "userId and key are required" };
        await axios.post(base, { userId, key, value: value ?? "" });
        setTick((t) => t + 1);
        return { success: true, key };
      },
    }),
    [base, userId],
  );

  return { clientTools, memories, refresh, remove, clear };
}
