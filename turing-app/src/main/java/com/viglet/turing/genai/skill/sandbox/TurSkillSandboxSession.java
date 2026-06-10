/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.skill.sandbox;

import java.io.File;

/**
 * T321 / §IX.4.d — an open, interactive skill sandbox session.
 *
 * <p>Unlike the one-shot Code Interpreter (each {@code execute_python} call
 * spawns a throwaway container and discards its filesystem), a skill session is
 * <strong>persistent across chat turns</strong>: it owns a stable host
 * directory whose {@link #workspaceDir()} survives between commands, and the
 * skill's Anthropic-compatible folder materialized read-only at
 * {@link #skillDir()}. The model drives the session through repeated
 * {@code bash} commands ({@link TurSkillSandboxService#runBash}), each of which
 * runs in a fresh hardened container that bind-mounts these two stable host
 * directories — so files written in one turn are visible in the next.
 *
 * <p>Session identity is the triple {@code (agentId, conversationId, skillId)}:
 * the same conversation talking to the same skill always resolves to the same
 * directories. {@code agentId}/{@code conversationId} may be blank for
 * debug/admin exec, in which case a {@code default} bucket is used.
 *
 * @param sessionId       short opaque id (also the container-name suffix).
 * @param skillId         the {@link com.viglet.turing.persistence.model.skill.TurSkill} id.
 * @param agentId         owning agent id, or {@code "default"} when unscoped.
 * @param conversationId  owning conversation id, or {@code "default"} when unscoped.
 * @param sessionDir      session root on the host (parent of skill/ + workspace/).
 * @param skillDir        host dir holding the materialized skill folder (mounted ro at {@code /skill}).
 * @param workspaceDir    persistent host scratch dir (mounted rw at {@code /workspace}).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public record TurSkillSandboxSession(
        String sessionId,
        String skillId,
        String agentId,
        String conversationId,
        File sessionDir,
        File skillDir,
        File workspaceDir) {

    /** Mount point inside the container for the read-only skill folder. */
    public static final String SKILL_MOUNT = "/skill";
    /** Mount point inside the container for the persistent writable workspace. */
    public static final String WORKSPACE_MOUNT = "/workspace";

    /** Stable key for the session cache: {@code agentId/conversationId/skillId}. */
    public String key() {
        return agentId + "/" + conversationId + "/" + skillId;
    }
}
