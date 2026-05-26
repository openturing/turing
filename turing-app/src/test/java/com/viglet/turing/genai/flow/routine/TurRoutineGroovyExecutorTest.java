/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.flow.routine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.viglet.turing.genai.flow.routine.TurRoutineGroovyExecutor.RoutineExecutionException;
import com.viglet.turing.persistence.model.agent.TurRoutine;
import com.viglet.turing.persistence.model.agent.TurRoutineKind;

/**
 * Pin tests for the GROOVY routine executor. Exercises the binding
 * contract (args / conversationId / routineName), the script-cache reuse
 * across invocations, and graceful failures on bad source / missing body.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurRoutineGroovyExecutorTest {

    @Test
    @DisplayName("happy path: last expression is returned as String, args auto-bound")
    void execute_happyPath() {
        TurRoutineGroovyExecutor exec = new TurRoutineGroovyExecutor();
        TurRoutine routine = groovyRoutine("greet",
                "\"Olá, ${args.name} (conv=${conversationId})\"");

        String result = exec.execute(routine, "conv-1", "{\"name\":\"Maria\"}");

        assertThat(result).isEqualTo("Olá, Maria (conv=conv-1)");
    }

    @Test
    @DisplayName("null/blank inputJson is treated as empty args, no NPE")
    void execute_blankInputJson_safe() {
        TurRoutineGroovyExecutor exec = new TurRoutineGroovyExecutor();
        TurRoutine routine = groovyRoutine("noop",
                "args.size() == 0 ? 'empty' : 'has-keys'");

        assertThat(exec.execute(routine, "c", null)).isEqualTo("empty");
        assertThat(exec.execute(routine, "c", "  ")).isEqualTo("empty");
    }

    @Test
    @DisplayName("invalid inputJson falls back to empty args + logs warning")
    void execute_invalidJson_treatedAsEmpty() {
        TurRoutineGroovyExecutor exec = new TurRoutineGroovyExecutor();
        TurRoutine routine = groovyRoutine("safe",
                "args.size() == 0 ? 'ok' : 'unexpected'");

        assertThat(exec.execute(routine, "c", "not-json")).isEqualTo("ok");
    }

    @Test
    @DisplayName("null script return becomes empty string (not the literal \"null\")")
    void execute_nullReturn_isEmpty() {
        TurRoutineGroovyExecutor exec = new TurRoutineGroovyExecutor();
        TurRoutine routine = groovyRoutine("voidish", "null");

        assertThat(exec.execute(routine, "c", "{}")).isEmpty();
    }

    @Test
    @DisplayName("script throwing surfaces as RoutineExecutionException")
    void execute_scriptThrows_wrapsInRoutineException() {
        TurRoutineGroovyExecutor exec = new TurRoutineGroovyExecutor();
        TurRoutine routine = groovyRoutine("bad",
                "throw new IllegalStateException('boom')");

        assertThatThrownBy(() -> exec.execute(routine, "c", "{}"))
                .isInstanceOf(RoutineExecutionException.class)
                .hasMessageContaining("boom");
    }

    @Test
    @DisplayName("blank script body fails fast with a clear message")
    void execute_blankBody_fails() {
        TurRoutineGroovyExecutor exec = new TurRoutineGroovyExecutor();
        TurRoutine routine = groovyRoutine("empty", "");

        assertThatThrownBy(() -> exec.execute(routine, "c", "{}"))
                .isInstanceOf(RoutineExecutionException.class)
                .hasMessageContaining("no groovyScript body");
    }

    @Test
    @DisplayName("null routine fails fast")
    void execute_nullRoutine_fails() {
        TurRoutineGroovyExecutor exec = new TurRoutineGroovyExecutor();

        assertThatThrownBy(() -> exec.execute(null, "c", "{}"))
                .isInstanceOf(RoutineExecutionException.class);
    }

    @Test
    @DisplayName("re-execute uses cached Class (no re-parse) when body is identical")
    void execute_cachesCompiledScript() {
        TurRoutineGroovyExecutor exec = new TurRoutineGroovyExecutor();
        TurRoutine routine = groovyRoutine("cached",
                "args.x == null ? 0 : args.x * 2");

        // Two invocations with same routine.id + body. Result must work
        // for both — the cache hit on the second call is exercised by
        // coverage, the assertion validates correctness end-to-end.
        assertThat(exec.execute(routine, "c", "{\"x\":3}")).isEqualTo("6");
        assertThat(exec.execute(routine, "c", "{\"x\":7}")).isEqualTo("14");
    }

    @Test
    @DisplayName("editing the body invalidates the cache (different hash → fresh parse)")
    void execute_recompilesOnBodyEdit() {
        TurRoutineGroovyExecutor exec = new TurRoutineGroovyExecutor();
        TurRoutine routine = groovyRoutine("editable", "'v1'");
        assertThat(exec.execute(routine, "c", "{}")).isEqualTo("v1");

        routine.setGroovyScript("'v2'");
        assertThat(exec.execute(routine, "c", "{}")).isEqualTo("v2");
    }

    @Test
    @DisplayName("routineName binding is set so logs can identify the running routine")
    void execute_exposesRoutineNameBinding() {
        TurRoutineGroovyExecutor exec = new TurRoutineGroovyExecutor();
        TurRoutine routine = groovyRoutine("named",
                "\"hi from ${routineName}\"");

        assertThat(exec.execute(routine, "c", "{}")).contains("named-");
    }

    private static TurRoutine groovyRoutine(String name, String body) {
        TurRoutine r = new TurRoutine();
        // Stable id per routine name + body so the cache key is meaningful
        // across tests but unique per scenario.
        r.setId("r-" + name + "-" + Integer.toHexString(body.hashCode()));
        r.setName(name + "-" + Integer.toHexString(body.hashCode()));
        r.setKind(TurRoutineKind.GROOVY);
        r.setGroovyScript(body);
        r.setDefaultTimeoutMs(30_000);
        r.setEnabled(true);
        return r;
    }
}
