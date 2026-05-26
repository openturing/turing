/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.chatslots;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.agent.TurChatSlotAuditEntry;
import com.viglet.turing.persistence.model.agent.TurChatSlotAuditSource;
import com.viglet.turing.persistence.repository.agent.TurChatSlotAuditRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * T60 — append-only audit log for every chat-flow slot mutation. All four
 * production write paths (node executor, Custom Tool {@code slots.set},
 * {@code POST /chat/slots}, document extraction) call
 * {@link #record(String, String, String, String, TurChatSlotAuditSource, String)}
 * synchronously with the (conversationId, slot, oldValue, newValue) tuple
 * and a source tag.
 *
 * <p>Defensive design choices:
 * <ul>
 *   <li>Same-value rewrites (where {@code oldValue.equals(newValue)}) are
 *       still recorded — even a "noop" write is operationally interesting
 *       (proves a node walked through, proves a tool reconfirmed the value),
 *       and excluding them would surprise admins who expect "every action
 *       leaves a trail".</li>
 *   <li>Persistence failures are swallowed (caught + logged) so the audit
 *       layer never breaks a production chat turn — the user-visible
 *       behaviour of every write path is identical with or without the
 *       audit subsystem running.</li>
 *   <li>Diff helpers {@link #diff(Map, Map)} extract the (added, updated)
 *       set between two slot maps without re-implementing the merge logic
 *       in every caller — the engine's transparent-walk path uses this to
 *       record individual NODE writes from a single end-of-walk snapshot.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurChatSlotAuditService {

    private final TurChatSlotAuditRepository repository;

    public TurChatSlotAuditService(TurChatSlotAuditRepository repository) {
        this.repository = repository;
    }

    /**
     * Persists one audit row. Blank / null conversation id or slot name is
     * a no-op (defensive: a buggy caller shouldn't pollute the log with
     * orphan rows that nothing can query back).
     *
     * @param conversationId active conversation id from {@code TUR_SESSION}
     * @param slotName       slot key; required
     * @param oldValue       value before the write ({@code null} when slot
     *                       was unset)
     * @param newValue       value after the write ({@code null} when slot
     *                       was cleared)
     * @param source         which subsystem produced the write
     * @param originDetail   optional free-text origin (e.g. node id,
     *                       custom tool name, route, agent id) — capped at
     *                       256 chars in storage
     */
    public void record(String conversationId, String slotName,
            String oldValue, String newValue,
            TurChatSlotAuditSource source, String originDetail) {
        if (conversationId == null || conversationId.isBlank()
                || slotName == null || slotName.isBlank()
                || source == null) {
            return;
        }
        try {
            TurChatSlotAuditEntry entry = new TurChatSlotAuditEntry();
            entry.setConversationId(conversationId);
            entry.setSlotName(slotName);
            // T61: PII slots persist their before/after values as the
            // redaction placeholder — the audit row still proves a write
            // happened (and at what time) without storing the secret
            // again, since plaintext would shortcut the encrypt-at-rest
            // guarantee that ChatFlowOps.writeVariables enforces.
            entry.setOldValue(com.viglet.turing.service.chatslots.TurPiiSlotService
                    .redactValue(slotName, oldValue));
            entry.setNewValue(com.viglet.turing.service.chatslots.TurPiiSlotService
                    .redactValue(slotName, newValue));
            entry.setSource(source);
            entry.setOriginDetail(truncate(originDetail));
            entry.setTs(LocalDateTime.now());
            repository.save(entry);
        } catch (RuntimeException e) {
            // Audit is best-effort: never fail a chat turn because of a
            // persistence hiccup on this side table.
            log.warn("[SlotAudit] failed to persist audit row for conv={} slot={} source={}: {}",
                    conversationId, slotName, source, e.getMessage());
        }
    }

    /**
     * Returns the audit trail of a conversation, oldest first. Empty list
     * when the conversation has no recorded writes — never throws.
     */
    public List<TurChatSlotAuditEntry> list(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return List.of();
        }
        return repository.findByConversationIdOrderByTsAsc(conversationId);
    }

    /**
     * Records every slot that differs between {@code before} and
     * {@code after}. Used by the engine's transparent-walk path: the
     * strategy mutates the variables map in-bulk and we don't want to wire
     * audit calls through every {@code ChatFlowOps.writeVariables} caller —
     * instead, we capture the previous + post snapshot and diff once.
     *
     * <p>Slots present only in {@code before} are reported as "cleared"
     * with {@code newValue=null}; slots present only in {@code after} as
     * "set" with {@code oldValue=null}; slots in both whose values differ
     * as "updated". Slots in both with equal values are skipped.
     */
    public void recordDiff(String conversationId,
            Map<String, String> before, Map<String, String> after,
            TurChatSlotAuditSource source, String originDetail) {
        if (conversationId == null || conversationId.isBlank() || source == null) {
            return;
        }
        Map<String, DiffEntry> diff = diff(before, after);
        for (Map.Entry<String, DiffEntry> e : diff.entrySet()) {
            record(conversationId, e.getKey(),
                    e.getValue().oldValue(), e.getValue().newValue(),
                    source, originDetail);
        }
    }

    /**
     * Pure-function helper exposed for tests + advanced callers: returns
     * the set of slots whose value differs between {@code before} and
     * {@code after}, keyed by slot name.
     */
    public static Map<String, DiffEntry> diff(Map<String, String> before, Map<String, String> after) {
        Map<String, String> b = before == null ? Map.of() : before;
        Map<String, String> a = after == null ? Map.of() : after;
        Map<String, DiffEntry> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : a.entrySet()) {
            String key = e.getKey();
            String prev = b.get(key);
            String next = e.getValue();
            if (!Objects.equals(prev, next)) {
                out.put(key, new DiffEntry(prev, next));
            }
        }
        for (Map.Entry<String, String> e : b.entrySet()) {
            if (!a.containsKey(e.getKey())) {
                out.put(e.getKey(), new DiffEntry(e.getValue(), null));
            }
        }
        return out;
    }

    /** (oldValue, newValue) pair returned by {@link #diff(Map, Map)}. */
    public record DiffEntry(String oldValue, String newValue) { }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 256 ? s : s.substring(0, 253) + "...";
    }
}
