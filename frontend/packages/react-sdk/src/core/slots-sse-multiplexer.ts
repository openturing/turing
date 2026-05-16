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
 *
 * <p>Each {@code useTuringSlots({ transport: "sse" })} call used to create
 * its own {@code EventSource}. On pages that mount the hook 3-5 times
 * (header pill, FlowOutputs cards, SlotInspector, area badge, …) that
 * meant N parallel SSE connections all carrying identical bytes —
 * wasted file-descriptors server-side and N× memory pressure on every
 * heartbeat. This module collapses them to one connection per
 * {@code (baseURL, site, conversationId)} triple.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>First subscriber for a key opens an {@code EventSource} via
 *       {@link openChannel}.</li>
 *   <li>Subsequent subscribers join the same channel. The latest cached
 *       snapshot is replayed asynchronously via {@code queueMicrotask}
 *       so a late component sees current state without waiting for the
 *       next server-side mutation.</li>
 *   <li>Each subscriber's {@link SlotsSseSubscription#close close} drops
 *       it from the channel's subscriber set; when the set empties, the
 *       {@code EventSource} is closed and the channel is removed from
 *       the registry. Next subscribe re-opens fresh.</li>
 *   <li>When the server closes the connection (readyState=CLOSED — 4xx,
 *       session expiry, …), every subscriber receives
 *       {@link SlotsSseSubscriber#onClosed} and the channel is torn
 *       down so the caller can fall back to polling.</li>
 * </ol>
 *
 * <h2>What is NOT done</h2>
 * <ul>
 *   <li><b>No debounced close.</b> Refcount → 0 closes immediately. In
 *       React 18 strict-mode dev, mount→unmount→mount opens 2 connections
 *       briefly; production is unaffected. The cost of being smarter
 *       (microtask-scheduled close that a re-subscribe can cancel)
 *       isn't worth the extra state machine for this use case.</li>
 *   <li><b>No retry/backoff.</b> {@code EventSource} already auto-reconnects
 *       on transient errors. We only act on the terminal CLOSED state.</li>
 *   <li><b>No cross-tab dedup.</b> Each tab's JS bundle has its own
 *       multiplexer — that's fine; the bandwidth cost is per-tab.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.7
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
   * Transient errors that {@code EventSource} auto-recovers from do
   * NOT trigger this callback.
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
      // Heartbeats (`: heartbeat`) never reach onmessage — EventSource
      // swallows comment-only frames. Anything that gets here AND fails
      // to parse is a genuinely corrupt JSON frame; drop it silently
      // to avoid breaking every subscriber on one bad payload.
      return;
    }
    channel.latestSnapshot = payload;
    // Snapshot the subscriber set so a re-entrant close() during one
    // subscriber's onMessage doesn't mutate the iteration in flight.
    const snapshotSubs = Array.from(channel.subscribers);
    for (const sub of snapshotSubs) {
      try {
        sub.onMessage(payload);
      } catch {
        // Per-subscriber error must not affect siblings. The subscriber
        // is responsible for its own error reporting (React state
        // errors surface via the next render).
      }
    }
  };
  eventSource.onerror = () => {
    // EventSource auto-reconnects when readyState=CONNECTING. CLOSED is
    // the terminal state — the server severed the connection and the
    // browser won't retry. Tear the channel down and notify subscribers
    // so they can fall back to polling.
    if (eventSource.readyState !== globalThis.EventSource.CLOSED) return;
    const snapshotSubs = Array.from(channel.subscribers);
    channel.subscribers.clear();
    channels.delete(key);
    for (const sub of snapshotSubs) {
      try {
        sub.onClosed();
      } catch {
        // Same posture as onMessage — never let one subscriber's error
        // break notification of the rest.
      }
    }
  };
  channels.set(key, channel);
  return channel;
}

/**
 * Subscribes a callback pair to the SSE stream of slot snapshots for
 * the given {@code (baseURL, site, conversationId)} triple. Joins the
 * existing connection when one is already open for that key, opens a
 * new one otherwise. Caller MUST invoke
 * {@link SlotsSseSubscription#close} on cleanup (typically returned
 * from a React effect).
 *
 * <p>If the channel already received at least one snapshot before this
 * subscription, the cached snapshot is replayed asynchronously via
 * {@code queueMicrotask}. The async tick lets the caller finish its
 * effect setup before {@code onMessage} fires for the first time —
 * matching the timing the {@code EventSource} itself would have given
 * a first-subscriber.
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

  // Late subscriber sees the latest snapshot without waiting for the
  // next mutation. Async so the order is consistent regardless of
  // whether this is the first or Nth subscriber.
  if (channel.latestSnapshot !== null) {
    const replayChannel = channel;
    const replaySnapshot = channel.latestSnapshot;
    globalThis.queueMicrotask(() => {
      // Guard: the subscriber may have already closed by the time the
      // microtask runs — common in React 18 strict-mode dev cycles.
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
 * Useful for the consumer's own assertions ("only one EventSource
 * after mounting 5 useTuringSlots") and for debugging
 * channel-leak symptoms. Not part of the public API surface — keep
 * out of {@code index.ts}.
 */
export function _slotsSseOpenChannelCount(): number {
  return channels.size;
}
