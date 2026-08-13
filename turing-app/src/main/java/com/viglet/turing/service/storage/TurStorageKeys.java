/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.service.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * The rule for turning caller-supplied text into an object key that cannot leave
 * the prefix it is joined to (T951 / §XXXVII.19).
 *
 * <p>It lived in {@code TurSkillFileService} and is now here because a second
 * caller needed it. A traversal check written twice is the shape T894 removed
 * from the security config: two copies, nothing holding them equal, and the one
 * that drifts is the one nobody is looking at.
 *
 * <p><strong>Why a caller cannot rely on the layers around it.</strong>
 * {@code StrictHttpFirewall} rejects {@code ..}, {@code %2e%2e} and
 * {@code %252e%252e} — but Turing enables {@code setAllowUrlEncodedSlash(true)},
 * and the firewall only rejects {@code .}/{@code ..} as <em>whole</em> segments,
 * so {@code ..%2f..%2fsecret} arrives intact and decodes to a traversal.
 * Underneath, {@code VigletFilesystemStorageService.resolveSafe} normalizes and
 * asserts containment — but that is a different artifact
 * ({@code viglet-core-storage}), and the MinIO backend has no such notion because
 * a key there is literal. Depending on either is depending on something this
 * repository does not own.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class TurStorageKeys {

    private TurStorageKeys() {
        // utility
    }

    /**
     * The path as a relative object key, with {@code .} segments dropped.
     *
     * @throws IllegalArgumentException when the path is blank, absolute, or
     *         contains a {@code ..} segment — the caller decides what that means
     *         for its own surface (a 400, a 404, a rejected write)
     */
    public static String safeRelative(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Path is required.");
        }
        // Backslashes are normalized first: on a Windows host they are separators,
        // so `..\..\secret` is the same attempt spelled differently.
        String normalized = relativePath.replace('\\', '/').trim();
        if (normalized.startsWith("/")) {
            throw new IllegalArgumentException("Path must be relative: " + relativePath);
        }
        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                throw new IllegalArgumentException("Path traversal is not allowed: " + relativePath);
            }
            segments.add(segment);
        }
        if (segments.isEmpty()) {
            throw new IllegalArgumentException("Path is required.");
        }
        return String.join("/", segments);
    }
}
