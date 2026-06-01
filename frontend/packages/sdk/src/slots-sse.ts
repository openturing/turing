/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
import type { TurChatSessionSlots } from "./api";

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

interface Channel {
  readonly eventSource: EventSource;
  latestSnapshot: TurChatSessionSlots | null;
  readonly subscribers: Set<SlotsSseSubscriber>;
}

const channels = new Map<string, Channel>();

function keyFor(baseURL: string, site: string, conversationId: string): string {
  return `${baseURL}|${site}|${conversationId}`;
}

function openChannel(
  key: string,
  baseURL: string,
  site: string,
  conversationId: string,
): Channel {
  const url = `${baseURL}/sn/${site}/chat/slots/stream?conversationId=${encodeURIComponent(
    conversationId,
  )}`;
  const eventSource = new globalThis.EventSource(url, { withCredentials: true });
  const channel: Channel = {
    eventSource,
    latestSnapshot: null,
    subscribers: new Set<SlotsSseSubscriber>(),
  };
  eventSource.onmessage = (ev) => {
    let payload: TurChatSessionSlots;
    try {
      payload = JSON.parse(ev.data) as TurChatSessionSlots;
    } catch {
      // Drop genuinely corrupt JSON frames silently rather than breaking
      // every subscriber on one bad payload.
      return;
    }
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
 * Subscribes a callback pair to the SSE stream of slot snapshots for the
 * given {@code (baseURL, site, conversationId)} triple. Joins the existing
 * connection when one is open for that key, opens a new one otherwise.
 * Caller MUST invoke {@link SlotsSseSubscription#close} on cleanup.
 */
export function subscribeSlotsSse(
  baseURL: string,
  site: string,
  conversationId: string,
  subscriber: SlotsSseSubscriber,
): SlotsSseSubscription {
  const key = keyFor(baseURL, site, conversationId);
  let channel = channels.get(key);
  if (channel === undefined) {
    channel = openChannel(key, baseURL, site, conversationId);
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
