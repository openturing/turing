/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
import type {
  TurWorkspaceArtifact,
  TurWorkspaceArtifacts,
  TurWorkspaceEvent,
} from "./api";

/**
 * Module-level SSE multiplexer for the {@code /chat/workspace/stream} endpoint
 * (T113). The exact analogue of {@link ./slots-sse} for the per-conversation
 * blob store: collapses N subscriptions for the same
 * {@code (baseURL, site, conversationId, agentId)} tuple onto a single
 * {@code EventSource}.
 *
 * <p>Unlike the slot stream — which pushes a full value map per event — the
 * workspace stream emits one {@link TurWorkspaceEvent} per blob write/delete
 * (metadata only). This multiplexer folds those single-artifact events into a
 * running map and surfaces complete {@link TurWorkspaceArtifacts} snapshots to
 * subscribers, so a consumer (the {@code useTuringWorkspace} hook, a portal
 * sidebar) just renders {@code artifacts} and never has to diff.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

export interface WorkspaceSseSubscriber {
  /**
   * Called with the reconstructed artifact list each time the server emits an
   * event, plus once asynchronously after subscription if the channel already
   * has a cached snapshot from a prior event.
   */
  readonly onMessage: (snapshot: TurWorkspaceArtifacts) => void;
  /**
   * Called when the channel terminates — server closed the connection
   * (readyState=CLOSED). The workspace stream has no polling fallback, so
   * subscribers typically surface a "disconnected" state and may resubscribe.
   */
  readonly onClosed: () => void;
}

export interface WorkspaceSseSubscription {
  /** Unsubscribe. Last unsubscribe closes the underlying connection. */
  close(): void;
}

/**
 * Selects which workspace stream to open. Provide {@code site} for the
 * site-scoped endpoint ({@code /sn/{site}/chat/workspace/stream}, with optional
 * {@code agentId} to back-fill the initial snapshot), or {@code agentId} alone
 * for the agent-scoped endpoint ({@code /v2/ai-agent/{agentId}/workspace/stream},
 * used by the authenticated admin console which has no site).
 */
export interface WorkspaceSseScope {
  readonly site?: string;
  readonly agentId?: string;
}

interface Channel {
  readonly eventSource: EventSource;
  latestSnapshot: TurWorkspaceArtifacts | null;
  /** Running artifact map, keyed by workspace key. */
  readonly artifacts: Map<string, TurWorkspaceArtifact>;
  readonly conversationId: string;
  readonly subscribers: Set<WorkspaceSseSubscriber>;
}

const channels = new Map<string, Channel>();

function keyFor(baseURL: string, scope: WorkspaceSseScope, conversationId: string): string {
  return `${baseURL}|${scope.site ?? ""}|${scope.agentId ?? ""}|${conversationId}`;
}

/**
 * Builds the stream URL for the scope. Site mode wins when {@code site} is set
 * (agentId rides along as a query param to back-fill the snapshot); otherwise
 * agent mode uses the path-scoped endpoint.
 */
function urlFor(baseURL: string, scope: WorkspaceSseScope, conversationId: string): string {
  const conv = `conversationId=${encodeURIComponent(conversationId)}`;
  if (scope.site) {
    let url = `${baseURL}/sn/${scope.site}/chat/workspace/stream?${conv}`;
    if (scope.agentId) {
      // agentId back-fills the initial snapshot (the store is scoped by
      // agent + conversation); live events arrive regardless of it.
      url += `&agentId=${encodeURIComponent(scope.agentId)}`;
    }
    return url;
  }
  if (scope.agentId) {
    return `${baseURL}/v2/ai-agent/${encodeURIComponent(scope.agentId)}/workspace/stream?${conv}`;
  }
  throw new Error("subscribeWorkspaceSse: scope must include `site` or `agentId`.");
}

/** Builds an immutable, key-sorted snapshot from the running map. */
function buildSnapshot(channel: Channel): TurWorkspaceArtifacts {
  const artifacts = Array.from(channel.artifacts.values()).sort((a, b) =>
    a.key.localeCompare(b.key),
  );
  return Object.freeze({ conversationId: channel.conversationId, artifacts });
}

function openChannel(
  key: string,
  baseURL: string,
  scope: WorkspaceSseScope,
  conversationId: string,
): Channel {
  const url = urlFor(baseURL, scope, conversationId);
  const eventSource = new globalThis.EventSource(url, { withCredentials: true });
  const channel: Channel = {
    eventSource,
    latestSnapshot: null,
    artifacts: new Map<string, TurWorkspaceArtifact>(),
    conversationId,
    subscribers: new Set<WorkspaceSseSubscriber>(),
  };
  const notify = () => {
    const snapshot = buildSnapshot(channel);
    channel.latestSnapshot = snapshot;
    for (const sub of Array.from(channel.subscribers)) {
      try {
        sub.onMessage(snapshot);
      } catch {
        // Per-subscriber error must not affect siblings.
      }
    }
  };
  eventSource.onmessage = (ev) => {
    let parsed: TurWorkspaceEvent;
    try {
      parsed = JSON.parse(ev.data) as TurWorkspaceEvent;
    } catch {
      // Drop genuinely corrupt JSON frames silently rather than breaking
      // every subscriber on one bad payload.
      return;
    }
    if (!parsed || typeof parsed.key !== "string") return;
    if (parsed.event === "delete") {
      channel.artifacts.delete(parsed.key);
    } else {
      channel.artifacts.set(parsed.key, {
        key: parsed.key,
        contentType: parsed.contentType ?? null,
        size: parsed.size ?? 0,
        signedUrl: parsed.signedUrl ?? null,
      });
    }
    notify();
  };
  eventSource.onerror = () => {
    // EventSource auto-reconnects when readyState=CONNECTING. CLOSED is the
    // terminal state — tear down and notify subscribers.
    if (eventSource.readyState !== globalThis.EventSource.CLOSED) return;
    const subs = Array.from(channel.subscribers);
    channel.subscribers.clear();
    channels.delete(key);
    for (const sub of subs) {
      try {
        sub.onClosed();
      } catch {
        // Never let one subscriber's error break notification of the rest.
      }
    }
  };
  channels.set(key, channel);
  return channel;
}

/**
 * Subscribes a callback pair to the SSE stream of workspace artifacts for the
 * given {@code (baseURL, scope, conversationId)}. Joins the existing connection
 * when one is open for that key, opens a new one otherwise. Caller MUST invoke
 * {@link WorkspaceSseSubscription#close} on cleanup.
 *
 * @param scope {@link WorkspaceSseScope} — site mode (with optional agentId
 *              back-fill) or agent mode (path-scoped).
 */
export function subscribeWorkspaceSse(
  baseURL: string,
  scope: WorkspaceSseScope,
  conversationId: string,
  subscriber: WorkspaceSseSubscriber,
): WorkspaceSseSubscription {
  const key = keyFor(baseURL, scope, conversationId);
  let channel = channels.get(key);
  if (channel === undefined) {
    channel = openChannel(key, baseURL, scope, conversationId);
  }
  channel.subscribers.add(subscriber);

  // Late subscriber sees the latest snapshot without waiting for the next
  // mutation. Async so ordering is consistent for first vs Nth subscriber.
  if (channel.latestSnapshot !== null) {
    const replayChannel = channel;
    const replaySnapshot = channel.latestSnapshot;
    globalThis.queueMicrotask(() => {
      if (!replayChannel.subscribers.has(subscriber)) return;
      try {
        subscriber.onMessage(replaySnapshot);
      } catch {
        // Same posture as the live-stream path.
      }
    });
  }

  return {
    close() {
      const ch = channels.get(key);
      if (ch === undefined) return;
      ch.subscribers.delete(subscriber);
      if (ch.subscribers.size === 0) {
        ch.eventSource.close();
        channels.delete(key);
      }
    },
  };
}

/**
 * Test/diagnostic helper — returns the number of distinct workspace channels
 * currently open.
 */
export function _workspaceSseOpenChannelCount(): number {
  return channels.size;
}
