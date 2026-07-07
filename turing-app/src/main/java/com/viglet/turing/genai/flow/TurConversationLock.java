/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Per-conversation serialization gate for flow-state mutations.
 *
 * <h2>Why</h2>
 *
 * A conversation's {@code TurChatFlowState} (cursor + captured slots) is mutated
 * by several independent HTTP requests that the client may fire concurrently on
 * the SAME conversation: the chat turn itself (cursor advance via the LLM judge)
 * and the side-channel write endpoints — {@code POST /chat/slots} (optimistic
 * slot writes), {@code /form-submit}, {@code /flow-select}, {@code /chat/resume}.
 *
 * <p>{@code TurChatFlowState} carries no {@code @Version}, and each operation is
 * a load-mutate-save under its own {@code @Transactional}. With no
 * serialization, two concurrent operations lost-update each other: a slot write
 * that loads the pre-advance state and saves it back AFTER the chat turn's
 * advance committed silently reverts the cursor — the visitor's answer is
 * dropped and the turn falls through to the site RAG. Real incident: clicking a
 * switch-option chip (which the client backed with an optimistic
 * {@code writeSlot} fired alongside the chat send) left the flow parked on the
 * question node and produced "...essa informação não está disponível...".
 *
 * <h2>The pattern</h2>
 *
 * Every entry point that mutates a conversation's flow state runs its critical
 * section through {@link #runExclusive(String, Supplier)} keyed by
 * {@code conversationId}. That makes the chat turn's
 * detect-continuation+advance atomic with respect to any concurrent slot /
 * form / flow-select write on the same conversation — the API owns the
 * interaction so callers never have to choreograph their requests.
 *
 * <h2>Implementation</h2>
 *
 * A fixed stripe array of {@link ReentrantLock} keyed by
 * {@code conversationId.hashCode()}. Striping keeps memory bounded (no per-id
 * map to grow/evict) at the cost of occasionally serializing two unrelated
 * conversations that hash to the same stripe — harmless. The lock is reentrant
 * so a section that nests another locked engine call on the same thread does
 * not self-deadlock. Acquisition is bounded by {@link #ACQUIRE_TIMEOUT_SECONDS};
 * on timeout we log and proceed unserialized rather than hang a request thread
 * (degrade to the legacy race, never to a deadlock).
 *
 * <p>Scope is per-JVM. A conversation's streaming turn is sticky to one node,
 * and the side-channel writes target that same conversation within the same
 * short window, so in-process striping covers the real contention. A clustered
 * deployment that loses node affinity would need a distributed lock (Hazelcast)
 * behind this same API — the call sites would not change.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
@Slf4j
@Component
public class TurConversationLock {

    /**
     * Number of stripes. Far above the realistic count of conversations
     * mutating concurrently on one node, so genuine collisions (two live
     * conversations sharing a stripe) are rare and only cost brief, correct
     * serialization.
     */
    private static final int STRIPE_COUNT = 512;

    /**
     * Upper bound on how long a request waits for the conversation lock. The
     * longest holder is a chat turn's pre-LLM advance (LLM-judge round-trip,
     * single-digit seconds); 30s leaves generous headroom while still failing
     * open instead of pinning a thread forever.
     */
    private static final long ACQUIRE_TIMEOUT_SECONDS = 30L;

    private final ReentrantLock[] stripes;

    public TurConversationLock() {
        this.stripes = new ReentrantLock[STRIPE_COUNT];
        for (int i = 0; i < STRIPE_COUNT; i++) {
            this.stripes[i] = new ReentrantLock();
        }
    }

    private ReentrantLock lockFor(String conversationId) {
        return stripes[Math.floorMod(conversationId.hashCode(), STRIPE_COUNT)];
    }

    /**
     * Runs {@code action} holding the exclusive lock for {@code conversationId}.
     * A blank conversationId has no shared state to protect, so it runs
     * directly. On lock-acquisition timeout the action still runs (degrading to
     * the unserialized path) so a request never hangs.
     */
    public <T> T runExclusive(String conversationId, Supplier<T> action) {
        if (!StringUtils.hasText(conversationId)) {
            return action.get();
        }
        ReentrantLock lock = lockFor(conversationId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[ConvLock] Timed out after {}s acquiring lock for conversation '{}' — "
                        + "proceeding without exclusivity", ACQUIRE_TIMEOUT_SECONDS, conversationId);
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[ConvLock] Interrupted acquiring lock for conversation '{}' — proceeding", conversationId);
            return action.get();
        } finally {
            if (acquired) {
                lock.unlock();
            }
        }
    }

    /** Void overload of {@link #runExclusive(String, Supplier)}. */
    public void runExclusive(String conversationId, Runnable action) {
        runExclusive(conversationId, () -> {
            action.run();
            return null;
        });
    }
}
