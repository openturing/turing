/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.genai.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.service.storage.TurNoOpStorageService;
import com.viglet.turing.service.storage.TurStorageContentTypes;
import com.viglet.turing.service.storage.TurStorageObjectStat;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.service.storage.TurStorageType;

import org.springframework.web.multipart.MultipartFile;

/**
 * Unit tests for {@link TurAgentWorkspaceService} (T111).
 *
 * <p>Uses an in-memory {@link TurStorageService} double whose
 * {@code listObjects} reproduces the single-level (non-recursive) semantics of
 * the real filesystem/MinIO backends, so the service's recursive walk + path
 * scoping are exercised faithfully.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
@ExtendWith(MockitoExtension.class)
class TurAgentWorkspaceServiceTest {

    private static final String AGENT = "agent-1";
    private static final String CONV = "conv-9";
    private static final String SCOPE = "tenants/agent-1/conv-9/workspace/";
    private static final byte[] PAYLOAD = "x".getBytes();

    @Mock
    private TurAgentWorkspaceUrlSigner urlSigner;

    private InMemoryStorage storage;
    private TurWorkspaceEventBus eventBus;
    private TurAgentWorkspaceService workspace;

    @BeforeEach
    void setUp() {
        storage = new InMemoryStorage();
        eventBus = new TurWorkspaceEventBus();
        workspace = new TurAgentWorkspaceService(storage, urlSigner, eventBus,
                new com.viglet.turing.observability.TurChatPipelineObservation(null));
    }

    @Test
    void putThenGetRoundTrips() {
        workspace.put(AGENT, CONV, "notes.txt", "hello".getBytes(StandardCharsets.UTF_8), "text/plain");

        // Stored at the tenant-scoped path.
        assertTrue(storage.blobs.containsKey(SCOPE + "notes.txt"));

        Optional<byte[]> got = workspace.get(AGENT, CONV, "notes.txt");
        assertTrue(got.isPresent());
        assertEquals("hello", new String(got.get(), StandardCharsets.UTF_8));
    }

    @Test
    void getMissingKeyReturnsEmpty() {
        assertTrue(workspace.get(AGENT, CONV, "nope.txt").isEmpty());
    }

    @Test
    void contentTypeGuessedWhenNull() {
        workspace.put(AGENT, CONV, "data.csv", "a,b".getBytes(StandardCharsets.UTF_8), null);
        assertEquals("text/csv", storage.contentTypes.get(SCOPE + "data.csv"));
    }

    @Test
    void listIsRecursiveAndScopeStripped() {
        when(urlSigner.signQueryString(anyString(), anyString(), anyString())).thenReturn("");
        workspace.put(AGENT, CONV, "a.txt", "1".getBytes(), "text/plain");
        workspace.put(AGENT, CONV, "reports/b.csv", "2".getBytes(), "text/csv");
        workspace.put(AGENT, CONV, "reports/2026/c.csv", "3".getBytes(), "text/csv");
        // Another conversation's blob must NOT leak in.
        workspace.put(AGENT, "conv-OTHER", "secret.txt", PAYLOAD, "text/plain");

        List<WorkspaceEntry> all = workspace.list(AGENT, CONV, null);
        TreeSet<String> keys = new TreeSet<>();
        all.forEach(e -> keys.add(e.key()));
        assertEquals(new TreeSet<>(List.of("a.txt", "reports/b.csv", "reports/2026/c.csv")), keys);
    }

    @Test
    void listByPrefixScopesToFolder() {
        when(urlSigner.signQueryString(anyString(), anyString(), anyString())).thenReturn("");
        workspace.put(AGENT, CONV, "a.txt", "1".getBytes(), "text/plain");
        workspace.put(AGENT, CONV, "reports/b.csv", "2".getBytes(), "text/csv");

        List<WorkspaceEntry> reports = workspace.list(AGENT, CONV, "reports/");
        assertEquals(1, reports.size());
        assertEquals("reports/b.csv", reports.get(0).key());
    }

    @Test
    void deleteRemovesBlob() {
        workspace.put(AGENT, CONV, "tmp.txt", PAYLOAD, "text/plain");
        workspace.delete(AGENT, CONV, "tmp.txt");
        assertFalse(storage.blobs.containsKey(SCOPE + "tmp.txt"));
        assertTrue(workspace.get(AGENT, CONV, "tmp.txt").isEmpty());
    }

    @Test
    void signedUrlContainsScopedParamsAndSignature() {
        lenient().when(urlSigner.signQueryString(AGENT, CONV, "reports/b.csv"))
                .thenReturn("?exp=999&sig=abc");

        String url = workspace.signedUrl(AGENT, CONV, "reports/b.csv");
        assertTrue(url.startsWith("/api/v2/workspace/file?"));
        assertTrue(url.contains("agentId=agent-1"));
        assertTrue(url.contains("conversationId=conv-9"));
        // key is URL-encoded ('/' → %2F)
        assertTrue(url.contains("key=reports%2Fb.csv"));
        assertTrue(url.contains("exp=999"));
        assertTrue(url.contains("sig=abc"));
    }

    @Test
    void pathTraversalKeyRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> workspace.put(AGENT, CONV, "../escape.txt", PAYLOAD, "text/plain"));
        assertThrows(IllegalArgumentException.class,
                () -> workspace.put(AGENT, CONV, "ok/../../escape.txt", PAYLOAD, "text/plain"));
    }

    @Test
    void pipeInKeyRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> workspace.signedUrl(AGENT, CONV, "a|b.txt"));
    }

    @Test
    void blankKeyAndBlankTenantRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> workspace.put(AGENT, CONV, "  ", PAYLOAD, "text/plain"));
        assertThrows(IllegalArgumentException.class,
                () -> workspace.put(" ", CONV, "a.txt", PAYLOAD, "text/plain"));
    }

    @Test
    void leadingSlashesStrippedFromKey() {
        workspace.put(AGENT, CONV, "/leading.txt", PAYLOAD, "text/plain");
        assertTrue(storage.blobs.containsKey(SCOPE + "leading.txt"));
    }

    @Test
    void putPublishesPutEventWithMetadata() {
        when(urlSigner.signQueryString(anyString(), anyString(), anyString())).thenReturn("?exp=1&sig=z");
        List<TurWorkspaceEvent> events = new ArrayList<>();
        var sub = eventBus.subscribe(CONV).subscribe(events::add);

        workspace.put(AGENT, CONV, "reports/b.csv", "a,b".getBytes(StandardCharsets.UTF_8), "text/csv");

        assertEquals(1, events.size());
        TurWorkspaceEvent ev = events.get(0);
        assertEquals(TurWorkspaceEvent.EVENT_PUT, ev.event());
        assertEquals(CONV, ev.conversationId());
        assertEquals("reports/b.csv", ev.key());
        assertEquals("text/csv", ev.contentType());
        assertEquals(3L, ev.size());
        assertTrue(ev.signedUrl().startsWith("/api/v2/workspace/file?"));
        sub.dispose();
    }

    @Test
    void deletePublishesDeleteEvent() {
        when(urlSigner.signQueryString(anyString(), anyString(), anyString())).thenReturn("");
        workspace.put(AGENT, CONV, "tmp.txt", PAYLOAD, "text/plain");

        List<TurWorkspaceEvent> events = new ArrayList<>();
        var sub = eventBus.subscribe(CONV).subscribe(events::add);
        workspace.delete(AGENT, CONV, "tmp.txt");

        assertEquals(1, events.size());
        assertEquals(TurWorkspaceEvent.EVENT_DELETE, events.get(0).event());
        assertEquals("tmp.txt", events.get(0).key());
        sub.dispose();
    }

    @Test
    void otherConversationsNotMixedInOnBus() {
        when(urlSigner.signQueryString(anyString(), anyString(), anyString())).thenReturn("");
        List<TurWorkspaceEvent> events = new ArrayList<>();
        var sub = eventBus.subscribe(CONV).subscribe(events::add);

        workspace.put(AGENT, "conv-OTHER", "x.txt", "1".getBytes(), "text/plain");
        assertTrue(events.isEmpty());
        sub.dispose();
    }

    @Test
    void disabledStorageGetAndListAreEmptyPutThrows() {
        TurAgentWorkspaceService disabled =
                new TurAgentWorkspaceService(new TurNoOpStorageService(), urlSigner, eventBus,
                        new com.viglet.turing.observability.TurChatPipelineObservation(null));
        assertTrue(disabled.get(AGENT, CONV, "a.txt").isEmpty());
        assertTrue(disabled.list(AGENT, CONV, null).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> disabled.put(AGENT, CONV, "a.txt", PAYLOAD, "text/plain"));
    }

    // -----------------------------------------------------------------
    // In-memory storage double with single-level listObjects semantics.
    // -----------------------------------------------------------------

    static class InMemoryStorage implements TurStorageService {
        final Map<String, byte[]> blobs = new LinkedHashMap<>();
        final Map<String, String> contentTypes = new LinkedHashMap<>();

        @Override
        public TurStorageType getType() {
            return TurStorageType.FILESYSTEM;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public void uploadStream(String objectName, InputStream in, long size, String contentType) {
            try {
                blobs.put(objectName, in.readAllBytes());
                contentTypes.put(objectName, contentType);
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
            return new java.io.ByteArrayInputStream(data);
        }

        @Override
        public void deleteObject(String objectName) {
            if (!blobs.containsKey(objectName)) {
                throw new IllegalStateException("Not found: " + objectName);
            }
            blobs.remove(objectName);
            contentTypes.remove(objectName);
        }

        /** Single-level listing: files directly under prefix + dir markers. */
        @Override
        public List<TurAssetItem> listObjects(String prefix) {
            String p = prefix == null ? "" : prefix;
            List<TurAssetItem> items = new ArrayList<>();
            TreeSet<String> seenDirs = new TreeSet<>();
            for (Map.Entry<String, byte[]> e : blobs.entrySet()) {
                String name = e.getKey();
                if (!name.startsWith(p)) {
                    continue;
                }
                String remainder = name.substring(p.length());
                int slash = remainder.indexOf('/');
                if (slash >= 0) {
                    String dir = p + remainder.substring(0, slash) + "/";
                    if (seenDirs.add(dir)) {
                        items.add(new TurAssetItem(dir, 0, "", "", true));
                    }
                } else if (!remainder.isEmpty()) {
                    items.add(new TurAssetItem(
                            name,
                            e.getValue().length,
                            TurStorageContentTypes.guessContentType(name),
                            "2026-06-03T00:00:00Z",
                            false));
                }
            }
            return items;
        }

        @Override
        public List<TurAssetItem> listAllObjects() {
            return List.of();
        }

        @Override
        public TurStorageObjectStat statObject(String objectName) {
            byte[] data = blobs.get(objectName);
            return new TurStorageObjectStat(objectName, data == null ? 0 : data.length,
                    contentTypes.get(objectName), "2026-06-03T00:00:00Z");
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
