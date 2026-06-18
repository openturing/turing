/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.persistence.model.agent;

/**
 * Lifecycle of a {@link TurChatHumanApproval} record (T119 / §IX.5.a).
 *
 * <ul>
 *   <li>{@link #PENDING} — notification fired, conversation parked, awaiting a
 *       decision.</li>
 *   <li>{@link #DECIDED} — an operator resolved it through the approval
 *       endpoint; the resolved value is in {@code decision}.</li>
 *   <li>{@link #TIMED_OUT} — no decision arrived within the node's timeout and
 *       the {@code timeoutBehavior} auto-resolved it.</li>
 *   <li>{@link #CANCELLED} — force-advanced by an admin (parked-conversations
 *       "unblock") without a real operator decision.</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public enum TurHumanApprovalStatus {
    PENDING,
    DECIDED,
    TIMED_OUT,
    CANCELLED
}
