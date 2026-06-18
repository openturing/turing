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

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.agent.TurRoutine;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * T48 GROOVY follow-up — executes the {@code groovyScript} body of a
 * {@link TurRoutine} when {@code kind=GROOVY}. The last expression of the
 * script is captured and returned as the routine result, which the worker
 * writes back to the {@code scheduleAgent} node's {@code outputVariable}
 * through the slot bus.
 *
 * <p><b>Bindings exposed to the script</b>:
 * <ul>
 *   <li>{@code args} — the parsed payload JSON as a {@code Map<String,Object>}
 *       (also each top-level key auto-bound as a variable, e.g.
 *       {@code cargo} for {@code {"cargo": "CEO"}}). Mirrors the Custom
 *       Tool contract so authors familiar with one carry over to the other.</li>
 *   <li>{@code conversationId} — id of the parked conversation. Useful
 *       for tools that need to look up state from elsewhere.</li>
 *   <li>{@code routineName} — name of the executing routine, for logging.</li>
 * </ul>
 *
 * <p><b>Why no {@code slots.set()}</b>: a {@code scheduleAgent} routine has
 * exactly one output slot (the node's {@code outputVariable}), and the
 * caller wires the script return value into it. If a routine needs to
 * write more slots than that, register it as a Custom Tool instead — the
 * functionCall + Custom Tool path supports the multi-slot mutate model.
 *
 * <p>Compile artifacts are cached per (routineId, script-hash) so script
 * reuse across multiple chat turns parses Groovy source once. Cache is
 * invalidated naturally when the script body changes (different hash).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@Slf4j
@Service
public class TurRoutineGroovyExecutor {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> ARGS_TYPE = new TypeReference<>() {
    };

    /**
     * Per-routine compiled-script cache. Key is the routine id; value
     * carries the parsed Class + the hash of the source it was compiled
     * from so a script edit (which changes the hash) forces a fresh
     * parse on the next call.
     */
    private final Map<String, CachedScript> scriptCache = new HashMap<>();

    /**
     * Runs the GROOVY routine against {@code inputJson} and the
     * conversation context, returning the script's last-expression value
     * as a String (or empty when the script returned {@code null}).
     *
     * @throws RoutineExecutionException when the script throws, parsing
     *         fails, or the source is blank
     */
    public String execute(TurRoutine routine, String conversationId, String inputJson) {
        if (routine == null) {
            throw new RoutineExecutionException("routine is null");
        }
        String source = routine.getGroovyScript();
        if (source == null || source.isBlank()) {
            throw new RoutineExecutionException("GROOVY routine '" + routine.getName()
                    + "' has no groovyScript body");
        }
        Map<String, Object> args = parseArgs(inputJson);
        Class<? extends Script> scriptClass = scriptFor(routine, source);
        Binding binding = new Binding(new HashMap<>(args));
        binding.setVariable("args", args);
        binding.setVariable("conversationId", conversationId);
        binding.setVariable("routineName", routine.getName());
        try {
            Script script = scriptClass.getDeclaredConstructor().newInstance();
            script.setBinding(binding);
            Object result = script.run();
            return result == null ? "" : result.toString();
        } catch (ReflectiveOperationException e) {
            throw new RoutineExecutionException(
                    "GROOVY routine '" + routine.getName() + "' failed to instantiate: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new RoutineExecutionException(
                    "GROOVY routine '" + routine.getName() + "' threw: " + e.getMessage(), e);
        }
    }

    private static Map<String, Object> parseArgs(String inputJson) {
        if (inputJson == null || inputJson.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = JSON.readValue(inputJson, ARGS_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (JacksonException e) {
            log.warn("[Routine/groovy] inputJson is not a JSON object — passing empty args: {}",
                    e.getMessage());
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private synchronized Class<? extends Script> scriptFor(TurRoutine routine, String source) {
        int hash = source.hashCode();
        CachedScript cached = scriptCache.get(routine.getId());
        if (cached != null && cached.hash == hash) {
            return cached.scriptClass;
        }
        // GroovyShell is heavy; we don't keep the shell around — only the
        // parsed Class. This matches TurCustomToolCallbackService and lets
        // garbage collection reclaim the shell + its ClassLoader between
        // edits.
        GroovyShell shell = new GroovyShell();
        Class<? extends Script> clazz = shell.getClassLoader().parseClass(
                source,
                "TurRoutine_" + routine.getId() + ".groovy");
        scriptCache.put(routine.getId(), new CachedScript(clazz, hash));
        return clazz;
    }

    private record CachedScript(Class<? extends Script> scriptClass, int hash) {
    }

    /** Thrown by {@link #execute(TurRoutine, String, String)} on any failure. */
    public static class RoutineExecutionException extends RuntimeException {
        public RoutineExecutionException(String message) {
            super(message);
        }

        public RoutineExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
