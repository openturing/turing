/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
import type { TurChatSessionSlots, TurChatSessionSlotsDelta } from "./api";

/**
 * Module-level SSE multiplexer for the {@code /chat/slots/stream} endpoint.
 * Collapses N {@code createSlotsController({ transport: "sse" })} subscriptions
 * for the same {@code (baseURL, site, conversationId)} triple onto a single
 * {@code EventSource}.
 *
 * <p>Ported verbatim from the React SDK's `core/slots-sse-multiplexer.ts` —
 * the original already took {@code baseURL} as a parameter (rather than
 * reading it from axios), so no adaptation was needed.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */

export interface SlotsSseSubscriber {
  /**
   * Called with the parsed slot snapshot each time the server emits one,
   * plus once asynchronously after subscription if the channel already
   * has a cached snapshot from a prior message.
   */
  readonly onMessage: (snapshot: TurChatSessionSlots) => void;
  /**
   * Called when the channel terminates — server closed the connection
   * (readyState=CLOSED). Subscribers should fall back to polling.
   */
  readonly onClosed: () => void;
}

export interface SlotsSseSubscription {
  /** Unsubscribe. Last unsubscribe closes the underlying connection. */
  close(): void;
}

/** Optional behaviour for a slots subscription. @since 2026.3.1 */
export interface SlotsSseOptions {
  /**
   * Consume the bandwidth-efficient delta stream
   * ({@code /chat/slots/stream/delta}, T63) instead of the full-snapshot
   * stream. The multiplexer reconstructs the running map from the deltas and
   * still surfaces complete {@link TurChatSessionSlots} snapshots to
   * subscribers, so the {@code onMessage} contract is unchanged. Defaults to
   * {@code false} (full-snapshot stream) for backward compatibility.
   */
  readonly delta?: boolean;
}

interface Channel {
  readonly eventSource: EventSource;
  latestSnapshot: TurChatSessionSlots | null;
  /** Running map maintained in delta mode (null in snapshot mode). */
  runningSlots: Record<string, string> | null;
  readonly subscribers: Set<SlotsSseSubscriber>;
}

const channels = new Map<string, Channel>();

function keyFor(baseURL: string, site: string, conversationId: string, delta: boolean): string {
  return `${baseURL}|${site}|${conversationId}|${delta ? "delta" : "snapshot"}`;
}

function openChannel(
  key: string,
  baseURL: string,
  site: string,
  conversationId: string,
  delta: boolean,
): Channel {
  const path = delta ? "slots/stream/delta" : "slots/stream";
  const url = `${baseURL}/sn/${site}/chat/${path}?conversationId=${encodeURIComponent(
    conversationId,
  )}`;
  const eventSource = new globalThis.EventSource(url, { withCredentials: true });
  const channel: Channel = {
    eventSource,
    latestSnapshot: null,
    runningSlots: delta ? {} : null,
    subscribers: new Set<SlotsSseSubscriber>(),
  };
  const notify = (payload: TurChatSessionSlots) => {
    channel.latestSnapshot = payload;
    const snapshotSubs = Array.from(channel.subscribers);
    for (const sub of snapshotSubs) {
      try {
        sub.onMessage(payload);
      } catch {
        // Per-subscriber error must not affect siblings.
      }
    }
  };
  eventSource.onmessage = (ev) => {
    let parsed: unknown;
    try {
      parsed = JSON.parse(ev.data);
    } catch {
      // Drop genuinely corrupt JSON frames silently rather than breaking
      // every subscriber on one bad payload.
      return;
    }
    if (delta) {
      notify(applyDelta(channel, parsed as TurChatSessionSlotsDelta, conversationId));
    } else {
      notify(parsed as TurChatSessionSlots);
    }
  };
  eventSource.onerror = () => {
    // EventSource auto-reconnects when readyState=CONNECTING. CLOSED is the
    // terminal state — tear down and notify subscribers to fall back to polling.
    if (eventSource.readyState !== globalThis.EventSource.CLOSED) return;
    const snapshotSubs = Array.from(channel.subscribers);
    channel.subscribers.clear();
    channels.delete(key);
    for (const sub of snapshotSubs) {
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
 * Applies one delta event to the channel's running map and returns the
 * reconstructed full snapshot. A {@code snapshot:true} event resets the map
 * (covers reconnects); incremental events upsert {@code added}/{@code updated}
 * and delete {@code removed}.
 */
function applyDelta(
  channel: Channel,
  delta: TurChatSessionSlotsDelta,
  conversationId: string,
): TurChatSessionSlots {
  let running = channel.runningSlots;
  if (running === null || delta.snapshot) {
    running = {};
  }
  for (const [k, v] of Object.entries(delta.added ?? {})) running[k] = v;
  for (const [k, v] of Object.entries(delta.updated ?? {})) running[k] = v;
  for (const k of delta.removed ?? []) delete running[k];
  channel.runningSlots = running;
  return { conversationId: delta.conversationId || conversationId, slots: { ...running } };
}

/**
 * Subscribes a callback pair to the SSE stream of slot snapshots for the
 * given {@code (baseURL, site, conversationId)} triple. Joins the existing
 * connection when one is open for that key, opens a new one otherwise.
 * Caller MUST invoke {@link SlotsSseSubscription#close} on cleanup.
 *
 * <p>Pass {@code options.delta = true} to consume the T63 delta stream — the
 * multiplexer reconstructs snapshots internally so the {@code onMessage}
 * contract is identical; only the wire bytes shrink.
 */
export function subscribeSlotsSse(
  baseURL: string,
  site: string,
  conversationId: string,
  subscriber: SlotsSseSubscriber,
  options?: SlotsSseOptions,
): SlotsSseSubscription {
  const delta = options?.delta === true;
  const key = keyFor(baseURL, site, conversationId, delta);
  let channel = channels.get(key);
  if (channel === undefined) {
    channel = openChannel(key, baseURL, site, conversationId, delta);
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
 * Test/diagnostic helper — returns the number of distinct
 * {@code (baseURL, site, conversationId)} channels currently open.
 */
export function _slotsSseOpenChannelCount(): number {
  return channels.size;
}
