package com.viglet.turing.service.chatmemory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.dto.agent.TurChatSessionMessageDto;
import com.viglet.turing.persistence.dto.agent.TurChatSessionMessagesDto;
import com.viglet.turing.persistence.model.agent.TurAIAgent;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Captures chat turns into a bounded in-memory queue and flushes them to the
 * configured {@link TurChatMemoryStore} on a per-agent cadence. Designed to
 * never block the request thread:
 *
 * <ul>
 *   <li>{@link #recordTurn} returns immediately after appending to the queue;
 *       the queue is bounded so a stuck downstream cannot exhaust the heap.</li>
 *   <li>A single scheduled worker drains the queue every {@code TICK_SECONDS}
 *       and applies each agent's {@code chatMemoryFlushIntervalMinutes} as a
 *       per-agent cooldown — turns are buffered until the cooldown elapses
 *       and then written as one batch per (agent, conversation) pair.</li>
 *   <li>{@link #recordTurn} is a no-op when the store is disabled or the
 *       agent has {@code chatMemoryEnabled = false}, so callers can invoke
 *       it unconditionally without worrying about feature flags.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.6
 */
@Slf4j
@Service
public class TurChatMemoryService {

    /**
     * Worker tick. We never check more often than this, so an agent
     * configured with a smaller flush interval still gets one round-trip per
     * tick — the cooldown only sets a lower bound. Keeping it short means
     * shutdown latency is also short.
     */
    private static final long TICK_SECONDS = 30L;

    /** Hard cap on queued events. Discards oldest first when full. */
    private static final int MAX_QUEUE_SIZE = 4096;

    private final TurChatMemoryStore store;

    /** Bounded FIFO of un-flushed turns. */
    private final LinkedBlockingDeque<QueuedEvent> queue = new LinkedBlockingDeque<>(MAX_QUEUE_SIZE);

    /** Per-agent cooldown bookkeeping: epoch ms of the last successful flush. */
    private final Map<String, Long> lastFlushAt = new ConcurrentHashMap<>();

    private ScheduledExecutorService worker;

    public TurChatMemoryService(TurChatMemoryStore store) {
        this.store = store;
    }

    @PostConstruct
    void start() {
        if (!store.isEnabled()) {
            log.info("Chat memory worker not started: store disabled (engine={})", store.getEngine());
            return;
        }
        worker = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "tur-chat-memory-worker");
            t.setDaemon(true);
            return t;
        });
        worker.scheduleWithFixedDelay(this::flushDue, TICK_SECONDS, TICK_SECONDS, TimeUnit.SECONDS);
        log.info("Chat memory worker started (engine={}, tick={}s)", store.getEngine(), TICK_SECONDS);
    }

    @PreDestroy
    void shutdown() {
        if (worker == null) return;
        worker.shutdown();
        try {
            // Final drain so a graceful shutdown doesn't lose buffered turns.
            flushAll();
        } finally {
            try {
                if (!worker.awaitTermination(5, TimeUnit.SECONDS)) {
                    worker.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                worker.shutdownNow();
            }
        }
    }

    /**
     * Enqueues a single completed turn. Safe to call from the request thread
     * (in-flight Flux pipeline). Returns silently when the agent disables
     * chat memory, when the store is disabled, or when the conversation id
     * is missing — no exception ever leaks to the caller.
     */
    public void recordTurn(String siteName,
                           TurAIAgent agent,
                           String conversationId,
                           String locale,
                           String userMessage,
                           String assistantMessage) {
        if (!store.isEnabled()) return;
        if (agent == null || !agent.isChatMemoryEnabled()) return;
        if (!StringUtils.hasText(conversationId)) return;
        if (!StringUtils.hasText(userMessage) && !StringUtils.hasText(assistantMessage)) return;

        TurChatMemoryEvent event = new TurChatMemoryEvent(
                siteName,
                agent.getId(),
                conversationId,
                locale,
                userMessage,
                assistantMessage,
                Instant.now(),
                Math.max(1, agent.getChatMemoryMaxMessages()));

        long flushIntervalMs = TimeUnit.MINUTES.toMillis(
                Math.max(1, agent.getChatMemoryFlushIntervalMinutes()));

        QueuedEvent queued = new QueuedEvent(event, flushIntervalMs);
        if (!queue.offer(queued)) {
            // Drop the oldest when the queue is full — chat memory is best-effort,
            // not a strong durability guarantee. Better to lose the oldest event
            // than block the request thread waiting for capacity.
            queue.pollFirst();
            queue.offer(queued);
            log.warn("Chat memory queue is full ({}); dropped oldest event", MAX_QUEUE_SIZE);
        }
    }

    /**
     * Reads the chronological list of role-tagged messages persisted for a
     * conversation. The list is capped at {@code limit} most-recent items
     * (clamped to the {@code [1, 1000]} range by the store). Returns an
     * empty {@code messages} list when chat memory is disabled or when no
     * row exists for the given {@code conversationId}.
     *
     * <p>The {@code conversationId} argument must match the value the SDK
     * wrote to the {@code TUR_SESSION} cookie when the chat started — the
     * same string the {@link TurChatMemoryEvent} batches use as their key.
     *
     * @since 2026.2.7
     */
    public TurChatSessionMessagesDto listMessages(String conversationId, int limit) {
        String engine = store.getEngine().name();
        boolean enabled = store.isEnabled();
        if (!enabled || conversationId == null || conversationId.isBlank()) {
            return new TurChatSessionMessagesDto(conversationId, engine, enabled, List.of(), null);
        }
        List<TurChatSessionMessageDto> messages = store.findMessages(conversationId, limit).stream()
                .map(m -> new TurChatSessionMessageDto(
                        asString(m.get("role")),
                        asString(m.get("content")),
                        asString(m.get("timestamp"))))
                .toList();
        return new TurChatSessionMessagesDto(conversationId, engine, enabled, messages, null);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Drains every event currently in the queue whose owning agent's flush
     * cooldown has elapsed, groups by (agentId, conversationId), and sends
     * one batch per group to the store.
     */
    private void flushDue() {
        try {
            doFlush(false);
        } catch (Exception e) {
            log.warn("Chat memory flush cycle failed: {}", e.getMessage());
        }
    }

    private void flushAll() {
        try {
            doFlush(true);
        } catch (Exception e) {
            log.warn("Chat memory final flush failed: {}", e.getMessage());
        }
    }

    private void doFlush(boolean ignoreCooldown) {
        if (queue.isEmpty()) return;
        long now = System.currentTimeMillis();

        // Snapshot the queue while preserving FIFO order so per-conversation
        // ordering is stable in the persisted batch.
        List<QueuedEvent> snapshot = new ArrayList<>();
        queue.drainTo(snapshot);
        if (snapshot.isEmpty()) return;

        Map<String, List<TurChatMemoryEvent>> readyByConv = new java.util.LinkedHashMap<>();
        List<QueuedEvent> deferred = new ArrayList<>();

        for (QueuedEvent qe : snapshot) {
            String agentId = qe.event.agentId();
            long lastFlush = lastFlushAt.getOrDefault(agentId, 0L);
            boolean ready = ignoreCooldown || (now - lastFlush) >= qe.flushIntervalMs;
            if (ready) {
                readyByConv
                        .computeIfAbsent(qe.event.conversationId(), k -> new ArrayList<>())
                        .add(qe.event);
            } else {
                deferred.add(qe);
            }
        }

        // Re-queue events not yet eligible (in original order).
        for (QueuedEvent qe : deferred) {
            queue.offerLast(qe);
        }

        for (Map.Entry<String, List<TurChatMemoryEvent>> e : readyByConv.entrySet()) {
            store.appendBatch(e.getKey(), e.getValue());
            // Mark cooldown per agent (assumes events in this batch share the
            // same agent — true because conversationId is per agent in practice).
            String agentId = e.getValue().getFirst().agentId();
            if (StringUtils.hasText(agentId)) {
                lastFlushAt.put(agentId, now);
            }
        }
    }

    /** Tuple holding the event plus the per-agent cooldown captured at enqueue time. */
    private record QueuedEvent(TurChatMemoryEvent event, long flushIntervalMs) {}
}
