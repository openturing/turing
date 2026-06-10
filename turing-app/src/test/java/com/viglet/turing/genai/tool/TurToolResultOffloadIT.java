/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.DefaultToolMetadata;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.viglet.turing.genai.workspace.TurAgentWorkspace;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.service.storage.TurStorageType;
import com.viglet.turing.testutil.AbstractTuringSpringIT;

/**
 * End-to-end integration test for the T114 auto-offload-large-tool-results
 * flow (§IX.3.d). Boots the full Spring context with
 * {@code turing.storage.type=filesystem} so the real
 * {@link com.viglet.turing.service.storage.TurStorageConfig} factory selects
 * the filesystem backend and {@link TurStorageService#isEnabled()} returns
 * {@code true} — which is exactly what flips
 * {@link TurToolCallbackPipeline} into offload mode.
 *
 * <p>The test drives the genuine bean wiring (no mocks):
 * <ol>
 *   <li>a tool that returns &gt; {@code inlineMaxChars} is decorated by the
 *       real pipeline → the result the LLM sees is a {@code workspace://}
 *       reference, and the full payload is written to disk under the
 *       conversation's workspace;</li>
 *   <li>the pipeline appends the always-present {@code workspace_read}
 *       tool (with its {@code .md} description applied);</li>
 *   <li>calling {@code workspace_read} with the offloaded key reads the
 *       original payload back through the real {@link TurAgentWorkspace} +
 *       filesystem storage round-trip.</li>
 * </ol>
 *
 * <p>Does NOT redeclare {@code @SpringBootTest} — it inherits the parent's
 * annotation (which sets {@code spring.jmx.enabled=true}); see the project
 * IT conventions.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurToolResultOffloadIT extends AbstractTuringSpringIT {

    private static final String AGENT_ID = "agent-offload-it";
    private static final String CONVERSATION_ID = "conv-offload-it";
    private static final String TOOL_NAME = "big_catalog";

    /** Per-run filesystem-storage root; reaped on JVM shutdown. */
    private static final Path STORAGE_DIR = Paths.get(System.getProperty("java.io.tmpdir"),
            "turing-offload-it-" + UUID.randomUUID());

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> deleteRecursively(STORAGE_DIR), "turing-offload-it-cleanup"));
    }

    @DynamicPropertySource
    static void enableFilesystemStorage(DynamicPropertyRegistry registry) {
        registry.add("turing.storage.type", () -> "filesystem");
        registry.add("turing.storage.filesystem.path",
                () -> STORAGE_DIR.toString().replace('\\', '/'));
        // Defaults would already enable offload; pin them so the test is
        // explicit and independent of future default changes.
        registry.add("turing.genai.tool-result-offload.enabled", () -> "true");
        registry.add("turing.genai.tool-result-offload.inline-max-chars", () -> "4096");
    }

    @Autowired
    private TurToolCallbackPipeline pipeline;

    @Autowired
    private TurAgentWorkspace workspace;

    @Autowired
    private TurStorageService storageService;

    @Test
    void largeToolResultIsOffloadedAndReadableViaWorkspaceRead() {
        // Sanity: the filesystem backend was selected and is enabled, so the
        // pipeline is in offload mode.
        assertThat(storageService.getType()).isEqualTo(TurStorageType.FILESYSTEM);
        assertThat(storageService.isEnabled()).isTrue();

        // A tool whose result blows past the 4096-char inline budget.
        String bigPayload = "{\"items\":[" + "\"x\",".repeat(2000) + "\"end\"]}";
        assertThat(bigPayload.length()).isGreaterThan(4096);

        ToolCallback[] decorated = pipeline.decorate(new FixedResultToolCallback(TOOL_NAME, bigPayload));

        // The pipeline offload-wrapped the tool AND appended workspace_read.
        assertThat(decorated).hasSize(2);
        ToolCallback bigTool = byName(decorated, TOOL_NAME);
        ToolCallback workspaceRead = byName(decorated, TurWorkspaceReadToolCallback.TOOL_NAME);
        assertThat(bigTool).isNotNull();
        assertThat(workspaceRead).isNotNull();
        // workspace_read picked up its .md description override through the pipeline.
        assertThat(workspaceRead.getToolDefinition().description())
                .contains("Read the full contents of a workspace artifact");

        ToolContext context = new ToolContext(Map.of(
                TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID, AGENT_ID,
                TurCustomToolCallbackService.TOOL_CONTEXT_CONVERSATION_ID, CONVERSATION_ID));

        // 1) Invoke the big tool — the LLM-visible result is just a reference.
        String reference = bigTool.call("{}", context);
        assertThat(reference)
                .startsWith("Stored at workspace://" + TurToolResultOffloadCallback.OFFLOAD_PREFIX + TOOL_NAME)
                .contains("Call workspace_read with key=");
        assertThat(reference.length()).isLessThan(bigPayload.length());

        String key = extractKey(reference);
        assertThat(key).startsWith(TurToolResultOffloadCallback.OFFLOAD_PREFIX + TOOL_NAME).endsWith(".json");

        // 2) The full payload really landed on disk in the scoped workspace.
        Optional<byte[]> stored = workspace.get(AGENT_ID, CONVERSATION_ID, key);
        assertThat(stored).isPresent();
        assertThat(new String(stored.get(), java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(bigPayload);

        // 3) The LLM resolves the reference through workspace_read → original payload.
        String readBack = workspaceRead.call("{\"key\":\"" + key + "\"}", context);
        assertThat(readBack).isEqualTo(bigPayload);
    }

    @Test
    void readingAMissingKeyReturnsAFriendlyMessage() {
        ToolCallback[] decorated = pipeline.decorate(new FixedResultToolCallback(TOOL_NAME, "small"));
        ToolCallback workspaceRead = byName(decorated, TurWorkspaceReadToolCallback.TOOL_NAME);
        assertThat(workspaceRead).isNotNull();

        ToolContext context = new ToolContext(Map.of(
                TurCustomToolCallbackService.TOOL_CONTEXT_AGENT_ID, AGENT_ID,
                TurCustomToolCallbackService.TOOL_CONTEXT_CONVERSATION_ID, CONVERSATION_ID));

        String out = workspaceRead.call("{\"key\":\"tool-results/does-not-exist.json\"}", context);
        assertThat(out).contains("No workspace artifact found");
    }

    private static ToolCallback byName(ToolCallback[] callbacks, String name) {
        for (ToolCallback cb : callbacks) {
            if (name.equals(cb.getToolDefinition().name())) {
                return cb;
            }
        }
        return null;
    }

    private static final Pattern KEY_PATTERN = Pattern.compile("key=\"([^\"]+)\"");

    private static String extractKey(String reference) {
        Matcher m = KEY_PATTERN.matcher(reference);
        assertThat(m.find()).as("reference must embed the workspace key").isTrue();
        return m.group(1);
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // best effort
                }
            });
        } catch (IOException ignored) {
            // best effort
        }
    }

    /**
     * Minimal {@link ToolCallback} standing in for any real tool — returns a
     * fixed result regardless of input. Exercises the pipeline exactly like a
     * native / MCP / custom tool would (the offload decorator is agnostic to
     * tool type).
     */
    private static final class FixedResultToolCallback implements ToolCallback {
        private final ToolDefinition definition;
        private final String result;

        FixedResultToolCallback(String name, String result) {
            this.definition = new DefaultToolDefinition(name, "returns a fixed payload",
                    "{\"type\":\"object\",\"properties\":{}}");
            this.result = result;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public ToolMetadata getToolMetadata() {
            return new DefaultToolMetadata(false);
        }

        @Override
        public String call(String toolInput) {
            return result;
        }

        @Override
        public String call(String toolInput, ToolContext toolContext) {
            return result;
        }
    }
}
