import { useEffect, useRef, useState } from "react";
import axios from "axios";
import type { TurWorkspaceArtifact, TurWorkspaceArtifacts } from "../core/api";
import { readTurSession, TUR_SESSION_DEFAULT_COOKIE_NAME } from "../core/session";
import { subscribeWorkspaceSse } from "../core/workspace-sse-multiplexer";
import { useOptionalTuringContext } from "../core/use-turing-context";

export type WorkspaceStatus = "idle" | "loading" | "success" | "error";

/**
 * Selects agent mode for {@link useTuringWorkspace}. When provided, the hook
 * streams from the agent-scoped endpoint ({@code /v2/ai-agent/{id}/workspace/stream})
 * and needs no {@code <TuringProvider>} / site — the authenticated admin
 * console's mode. When omitted, the hook uses the site-scoped endpoint and
 * reads the site name from context.
 *
 * @since 2026.3.1
 */
export interface UseTuringWorkspaceAgent {
  readonly id: string;
}

export interface UseTuringWorkspaceOptions {
  /**
   * Forces the conversation id the stream keys on. When omitted, the hook
   * reads the {@code TUR_SESSION} cookie set by {@link useTuringChat} — i.e.
   * "show me the artifacts this agent built in the visitor's current tab".
   */
  readonly conversationId?: string;
  /**
   * Cookie name used to read the conversation id when {@code conversationId}
   * is not passed. Defaults to {@link TUR_SESSION_DEFAULT_COOKIE_NAME}.
   */
  readonly sessionCookieName?: string;
  /**
   * Opt into agent mode: streams from {@code GET /v2/ai-agent/{id}/workspace/stream}
   * instead of the site endpoint. Use when the consumer knows the exact agent
   * and has no site (the authenticated console). Mutually exclusive with the
   * site-mode {@link agentId} back-fill below.
   */
  readonly agent?: UseTuringWorkspaceAgent;
  /**
   * Site mode only — agent id used to back-fill the initial artifact snapshot.
   * The workspace store is scoped by agent + conversation, so without it the
   * site-mode stream still delivers <em>live</em> writes but cannot replay
   * artifacts created before the subscription. Ignored in agent mode (the
   * agent rides in the path there). Defaults to undefined.
   */
  readonly agentId?: string;
  /** Disables the hook entirely when {@code false}. Defaults to {@code true}. */
  readonly enabled?: boolean;
}

export interface UseTuringWorkspaceReturn {
  /** Artifacts currently in the conversation's workspace, sorted by key. */
  artifacts: readonly TurWorkspaceArtifact[];
  /** Conversation id the artifacts were read for, or {@code null} when none yet. */
  conversationId: string | null;
  /** Status of the stream. */
  status: WorkspaceStatus;
  /** Error message when the stream closed unexpectedly. */
  error: string | null;
}

const EMPTY_ARTIFACTS: readonly TurWorkspaceArtifact[] = Object.freeze([]);

/**
 * Subscribes to the agent's per-conversation workspace artifact stream (T113)
 * and exposes the live artifact list — "files this agent built for you". Each
 * artifact carries a {@code signedUrl} the user can download; the bytes never
 * touch the prompt.
 *
 * <p>Pairs with {@link useTuringChat}: both key off the same {@code TUR_SESSION}
 * cookie, so the artifacts returned here belong to the same conversation the
 * chat is sending messages on. SSE-only — there is no polling REST fallback for
 * the workspace; when the channel closes, {@code status} flips to {@code error}.
 *
 * @example
 * ```tsx
 * const { artifacts } = useTuringWorkspace({ agentId });
 * return (
 *   <ul>
 *     {artifacts.map((a) => (
 *       <li key={a.key}>
 *         <a href={a.signedUrl ?? "#"}>{a.key}</a> ({a.size} bytes)
 *       </li>
 *     ))}
 *   </ul>
 * );
 * ```
 *
 * @since 2026.3.1
 */
export function useTuringWorkspace(
  options: UseTuringWorkspaceOptions = {},
): UseTuringWorkspaceReturn {
  // Agent mode never reads the site; site mode needs the provider for
  // `config.site`. Use the optional context so an admin console with no real
  // "site" can mount the agent-mode variant without a placeholder provider.
  const ctx = useOptionalTuringContext();
  const {
    conversationId: explicitConversationId,
    sessionCookieName = TUR_SESSION_DEFAULT_COOKIE_NAME,
    agent,
    agentId,
    enabled = true,
  } = options;
  const modeAgentId = agent?.id;
  if (!ctx && !modeAgentId) {
    throw new Error(
      "useTuringWorkspace in site mode requires a <TuringProvider> ancestor. " +
        "Either mount the provider or pass `options.agent` to use agent mode.",
    );
  }
  const siteName = ctx?.config.site;

  const initialConversationId =
    explicitConversationId ?? readTurSession(sessionCookieName);
  const [conversationId, setConversationId] = useState<string | null>(
    initialConversationId,
  );
  const [artifacts, setArtifacts] =
    useState<readonly TurWorkspaceArtifact[]>(EMPTY_ARTIFACTS);
  const [status, setStatus] = useState<WorkspaceStatus>("idle");
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  // Track the conversation we last streamed for, to clear stale artifacts when
  // the visitor's session changes mid-mount.
  const lastConversationRef = useRef<string | null>(null);

  // Keep `conversationId` in sync with the explicit option / cookie. The cookie
  // may be minted by the chat hook on the first `send`, so re-checking on a
  // light interval lets the workspace start streaming as soon as it appears.
  useEffect(() => {
    const next = explicitConversationId ?? readTurSession(sessionCookieName);
    setConversationId((current) => (current === next ? current : next));
  }, [explicitConversationId, sessionCookieName, tick]);

  useEffect(() => {
    if (!enabled || conversationId) return;
    const handle = globalThis.setInterval(() => setTick((t) => t + 1), 2000);
    return () => globalThis.clearInterval(handle);
  }, [enabled, conversationId]);

  useEffect(() => {
    if (!enabled || !conversationId) return;
    if (globalThis.EventSource === undefined) {
      setStatus("error");
      setError("EventSource is not supported in this environment.");
      return;
    }
    setStatus("loading");
    setError(null);
    const baseURL = axios.defaults.baseURL ?? "";
    // Agent mode → path-scoped agent endpoint (no site). Site mode → site
    // endpoint, with `agentId` riding along to back-fill the initial snapshot.
    const scope = modeAgentId
      ? { agentId: modeAgentId }
      : { site: siteName, agentId };
    const subscription = subscribeWorkspaceSse(baseURL, scope, conversationId, {
      onMessage: (payload: TurWorkspaceArtifacts) => {
        lastConversationRef.current = conversationId;
        setArtifacts(payload.artifacts ?? EMPTY_ARTIFACTS);
        setStatus("success");
      },
      onClosed: () => {
        setStatus("error");
        setError("Workspace stream closed.");
      },
    });
    return () => subscription.close();
  }, [enabled, conversationId, siteName, modeAgentId, agentId]);

  // Drop cached artifacts when the conversation flips to a different session.
  useEffect(() => {
    if (
      lastConversationRef.current &&
      lastConversationRef.current !== conversationId
    ) {
      lastConversationRef.current = null;
      setArtifacts(EMPTY_ARTIFACTS);
    }
  }, [conversationId]);

  return { artifacts, conversationId, status, error };
}
