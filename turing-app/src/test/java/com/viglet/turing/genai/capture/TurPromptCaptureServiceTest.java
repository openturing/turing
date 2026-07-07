/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.capture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.genai.TurAgentChatExecutor.ChatMessageItem;
import com.viglet.turing.persistence.model.agent.TurAIAgent;
import com.viglet.turing.service.storage.TurStorageContentTypes;
import com.viglet.turing.service.storage.TurStorageObjectStat;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.service.storage.TurStorageType;

/**
 * Unit tests for {@link TurPromptCaptureService} (T618) — capture/read round
 * trips, the opt-in + storage gate, the per-conversation ring, and the
 * per-entry byte cap. Uses an in-memory {@link TurStorageService} double with
 * single-level {@code listObjects} semantics like the real backends.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurPromptCaptureServiceTest {

    private static final String AGENT = "agent-1";
    private static final String CONV = "conv-9";

    private InMemoryStorage storage;
    private TurPromptCaptureProperties properties;
    private TurPromptCaptureService service;

    @BeforeEach
    void setUp() {
        storage = new InMemoryStorage();
        properties = new TurPromptCaptureProperties();
        service = new TurPromptCaptureService(storage, properties);
    }

    private static TurAIAgent agent(boolean captureEnabled) {
        TurAIAgent agent = new TurAIAgent();
        agent.setId(AGENT);
        agent.setPromptCaptureEnabled(captureEnabled);
        return agent;
    }

    @Test
    void springAiCaptureRoundTrips() {
        List<Message> messages = List.of(
                new SystemMessage("SYS"),
                new UserMessage("hi"),
                new AssistantMessage("hello"),
                new UserMessage("again"));

        service.captureSpringAi(agent(true), CONV, messages);

        // Two user messages -> turn index 2.
        Optional<TurPromptCapture> loaded = service.getCapture(AGENT, CONV, 2);
        assertTrue(loaded.isPresent());
        TurPromptCapture capture = loaded.get();
        assertEquals(2, capture.turnIndex());
        assertEquals("SYS", capture.systemPrompt());
        assertEquals(4, capture.messages().size());
        assertEquals("system", capture.messages().get(0).role());
        assertEquals("assistant", capture.messages().get(2).role());
        assertEquals("again", capture.messages().get(3).content());
    }

    @Test
    void nativeCaptureRoundTrips() {
        List<ChatMessageItem> history = List.of(
                new ChatMessageItem("user", "q1"),
                new ChatMessageItem("assistant", "a1"),
                new ChatMessageItem("user", "q2"));

        service.captureNative(agent(true), CONV, "NATIVE-SYS", history);

        Optional<TurPromptCapture> loaded = service.getCapture(AGENT, CONV, 2);
        assertTrue(loaded.isPresent());
        TurPromptCapture capture = loaded.get();
        assertEquals(2, capture.turnIndex());
        assertEquals("NATIVE-SYS", capture.systemPrompt());
        // system prepended to the flat history.
        assertEquals(4, capture.messages().size());
        assertEquals("system", capture.messages().get(0).role());
        assertEquals("NATIVE-SYS", capture.messages().get(0).content());
    }

    @Test
    void disabledAgentDoesNotCapture() {
        service.captureSpringAi(agent(false), CONV, List.of(new SystemMessage("S"), new UserMessage("u")));
        assertTrue(storage.blobs.isEmpty());
        assertFalse(service.isEnabled(agent(false)));
    }

    @Test
    void disabledStorageDoesNotCapture() {
        storage.enabled = false;
        service.captureSpringAi(agent(true), CONV, List.of(new SystemMessage("S"), new UserMessage("u")));
        assertTrue(storage.blobs.isEmpty());
    }

    @Test
    void listTurnsNewestFirst() {
        captureTurns(5);
        List<TurPromptCaptureTurnRef> turns = service.listTurns(AGENT, CONV);
        assertEquals(List.of(5, 4, 3, 2, 1),
                turns.stream().map(TurPromptCaptureTurnRef::turnIndex).toList());
    }

    @Test
    void ringPrunesOldestTurns() {
        properties.setMaxTurnsPerConversation(3);
        captureTurns(6);
        List<TurPromptCaptureTurnRef> turns = service.listTurns(AGENT, CONV);
        // newest 3 kept.
        assertEquals(List.of(6, 5, 4),
                turns.stream().map(TurPromptCaptureTurnRef::turnIndex).toList());
    }

    @Test
    void oversizedCaptureSkipped() {
        properties.setMaxBytesPerEntry(8); // any real capture serializes larger
        service.captureSpringAi(agent(true), CONV,
                List.of(new SystemMessage("a very long system prompt that will not fit"),
                        new UserMessage("hello")));
        assertTrue(storage.blobs.isEmpty());
        assertTrue(service.listTurns(AGENT, CONV).isEmpty());
    }

    @Test
    void missingTurnReturnsEmpty() {
        assertTrue(service.getCapture(AGENT, CONV, 99).isEmpty());
    }

    /** Captures {@code n} turns, turn i carrying i user messages so indices are 1..n. */
    private void captureTurns(int n) {
        for (int i = 1; i <= n; i++) {
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage("SYS"));
            for (int u = 0; u < i; u++) {
                messages.add(new UserMessage("u" + u));
            }
            service.captureSpringAi(agent(true), CONV, messages);
        }
    }

    // -----------------------------------------------------------------
    // In-memory storage double with single-level listObjects semantics.
    // -----------------------------------------------------------------

    static class InMemoryStorage implements TurStorageService {
        final Map<String, byte[]> blobs = new LinkedHashMap<>();
        boolean enabled = true;

        @Override
        public TurStorageType getType() {
            return TurStorageType.FILESYSTEM;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void uploadStream(String objectName, InputStream in, long size, String contentType) {
            try {
                blobs.put(objectName, in.readAllBytes());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public InputStream downloadObject(String objectName) {
            byte[] data = blobs.get(objectName);
            if (data == null) {
                throw new IllegalStateException("Not found: " + objectName);
            }
            return new ByteArrayInputStream(data);
        }

        @Override
        public List<TurAssetItem> listObjects(String prefix) {
            String p = prefix == null ? "" : prefix;
            List<TurAssetItem> items = new ArrayList<>();
            for (Map.Entry<String, byte[]> e : blobs.entrySet()) {
                String name = e.getKey();
                if (!name.startsWith(p)) {
                    continue;
                }
                String remainder = name.substring(p.length());
                if (!remainder.isEmpty() && remainder.indexOf('/') < 0) {
                    items.add(new TurAssetItem(name, e.getValue().length,
                            TurStorageContentTypes.guessContentType(name),
                            "2026-06-03T00:00:00Z", false));
                }
            }
            return items;
        }

        @Override
        public List<TurAssetItem> listAllObjects() {
            List<TurAssetItem> items = new ArrayList<>();
            for (Map.Entry<String, byte[]> e : blobs.entrySet()) {
                items.add(new TurAssetItem(e.getKey(), e.getValue().length,
                        "application/json", "2026-06-03T00:00:00Z", false));
            }
            return items;
        }

        @Override
        public TurStorageObjectStat statObject(String objectName) {
            byte[] data = blobs.get(objectName);
            return new TurStorageObjectStat(objectName, data == null ? 0 : data.length,
                    "application/json", "2026-06-03T00:00:00Z");
        }

        @Override
        public void deleteObject(String objectName) {
            blobs.remove(objectName);
        }

        @Override
        public void uploadObject(MultipartFile file, String prefix) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void createFolder(String folderPath) {
            // no-op
        }

        @Override
        public void deleteObjectsWithPrefix(String prefix) {
            blobs.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }
}
