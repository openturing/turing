/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.core.storage;

import java.util.Map;

/**
 * Shared utility for guessing MIME content types from file extensions.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class VigletStorageContentTypes {

    public static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private static final Map<String, String> EXTENSION_TO_CONTENT_TYPE = Map.ofEntries(
            Map.entry(".pdf", "application/pdf"),
            Map.entry(".png", "image/png"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".gif", "image/gif"),
            Map.entry(".svg", "image/svg+xml"),
            Map.entry(".txt", "text/plain"),
            Map.entry(".csv", "text/csv"),
            Map.entry(".json", "application/json"),
            Map.entry(".xml", "application/xml"),
            Map.entry(".html", "text/html"),
            Map.entry(".htm", "text/html"),
            Map.entry(".css", "text/css"),
            Map.entry(".js", "application/javascript"),
            Map.entry(".mjs", "application/javascript"),
            Map.entry(".woff", "font/woff"),
            Map.entry(".woff2", "font/woff2"),
            Map.entry(".ttf", "font/ttf"),
            Map.entry(".ico", "image/x-icon"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".zip", "application/zip"),
            Map.entry(".doc", "application/msword"),
            Map.entry(".docx", "application/msword"),
            Map.entry(".xls", "application/vnd.ms-excel"),
            Map.entry(".xlsx", "application/vnd.ms-excel")
    );

    private VigletStorageContentTypes() {
    }

    public static String guessContentType(String name) {
        if (name == null) return DEFAULT_CONTENT_TYPE;
        String lower = name.toLowerCase();
        return EXTENSION_TO_CONTENT_TYPE.entrySet().stream()
                .filter(entry -> lower.endsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(DEFAULT_CONTENT_TYPE);
    }
}
