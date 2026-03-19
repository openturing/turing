/*
 * Copyright (C) 2016-2025 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.genai;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared utility methods for RAG operations (text chunking, document creation).
 * Used by both SN GenAI and Asset Training pipelines.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
public final class TurRagUtils {

    public static final int DEFAULT_CHUNK_SIZE = 1024;
    public static final int MAX_TEXT_LENGTH = 100_000;

    private TurRagUtils() {
    }

    public static List<String> splitText(String text, int chunkSize) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (text.length() <= chunkSize) {
            return List.of(text);
        }
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < text.length(); start += chunkSize) {
            chunks.add(text.substring(start, Math.min(text.length(), start + chunkSize)));
        }
        return chunks;
    }

    public static List<String> splitText(String text) {
        return splitText(text, DEFAULT_CHUNK_SIZE);
    }

    public static List<Document> createDocuments(String text, int chunkSize, Map<String, Object> metadata) {
        return splitText(text, chunkSize).stream()
                .map(chunk -> new Document(chunk, metadata))
                .toList();
    }

    public static List<Document> createDocuments(String text, Map<String, Object> metadata) {
        return createDocuments(text, DEFAULT_CHUNK_SIZE, metadata);
    }

    public static String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }
}
