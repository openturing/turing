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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.safety.Safelist;
import org.springframework.ai.document.Document.Builder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Shared utility methods for RAG operations (HTML sanitization, semantic
 * chunking, document creation). Used by both SN GenAI and Asset Training
 * pipelines.
 *
 * <p>Indexing pipeline:</p>
 * <ol>
 *   <li>{@link #stripHtml(String)} — when the source attribute contains
 *       markup, tags are removed and whitespace is normalized so that
 *       embeddings reflect semantic content, not HTML structure.</li>
 *   <li>{@link #splitText(String, int)} — splits the cleaned text by
 *       paragraph → sentence → word boundaries, never cutting words
 *       mid-string. Adjacent chunks share a small overlap to preserve
 *       context across boundaries.</li>
 * </ol>
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
public final class TurRagUtils {

    /**
     * Maximum chunk size in characters. Roughly tuned for typical embedding
     * models (1024 chars ≈ 250 tokens), well within 8K-token model limits.
     */
    public static final int DEFAULT_CHUNK_SIZE = 1024;
    public static final int MAX_TEXT_LENGTH = 100_000;

    private static final Pattern HTML_TAG_HINT = Pattern.compile("<\\s*[a-zA-Z!/][^>]*>");
    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\n{2,}");
    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?…])\\s+(?=[A-ZÀ-Ý0-9\"'(\\[])");
    private static final Pattern WHITESPACE = Pattern.compile("[ \\t\\x0B\\f]+");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");

    private TurRagUtils() {
    }

    /**
     * Strips HTML markup from {@code text} when it looks like HTML, decoding
     * entities and normalizing whitespace. Block-level elements (p, div, li,
     * h1–h6, br, …) become paragraph breaks (\n\n) so the chunker can split
     * along semantic boundaries afterwards.
     * <p>
     * Returns the original input unchanged when no markup is detected, so
     * plain-text fields incur no overhead.
     */
    public static String stripHtml(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        if (!HTML_TAG_HINT.matcher(text).find()) {
            return text;
        }
        Document doc = Jsoup.parseBodyFragment(text);
        doc.outputSettings()
                .prettyPrint(false)
                .escapeMode(Entities.EscapeMode.xhtml);
        // Replace block-level elements with newlines and <br> with single newline
        // so paragraph splitting works downstream.
        doc.select("br").after("\\n");
        doc.select("p, div, li, h1, h2, h3, h4, h5, h6, blockquote, tr, table, article, section, header, footer, hr")
                .after("\\n\\n");
        String cleaned = Jsoup.clean(doc.body().html(), "", Safelist.none(),
                new Document.OutputSettings().prettyPrint(false));
        // Jsoup.clean re-escapes; decode \\n placeholders back to real newlines and entities to chars.
        cleaned = cleaned.replace("\\n", "\n");
        cleaned = org.jsoup.parser.Parser.unescapeEntities(cleaned, false);
        return normalizeWhitespace(cleaned);
    }

    /**
     * Collapses runs of spaces/tabs to a single space and runs of 3+ newlines
     * to two, while preserving paragraph breaks (\n\n).
     */
    public static String normalizeWhitespace(String text) {
        if (text == null) {
            return "";
        }
        String s = text.replace("\r\n", "\n").replace('\r', '\n');
        s = WHITESPACE.matcher(s).replaceAll(" ");
        s = MULTI_NEWLINE.matcher(s).replaceAll("\n\n");
        return s.trim();
    }

    /**
     * Splits {@code text} into chunks no larger than {@code chunkSize}
     * characters, respecting paragraph and sentence boundaries. Falls back to
     * word-aware splitting only when a single sentence is itself longer than
     * the chunk size — words are never cut in the middle. Adjacent
     * paragraph/sentence units are packed together as long as the total stays
     * under {@code chunkSize}; otherwise each unit gets its own chunk so that
     * paragraph-level context is preserved end-to-end.
     */
    public static List<String> splitText(String text, int chunkSize) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        if (chunkSize <= 0) {
            return List.of(text);
        }
        String normalized = normalizeWhitespace(text);
        if (normalized.length() <= chunkSize) {
            return List.of(normalized);
        }

        List<String> units = splitIntoUnits(normalized, chunkSize);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            if (unit.isEmpty()) {
                continue;
            }
            if (current.length() == 0) {
                current.append(unit);
                continue;
            }
            // +1 for the space/separator added between units
            if (current.length() + 1 + unit.length() <= chunkSize) {
                current.append(' ').append(unit);
            } else {
                chunks.add(current.toString().trim());
                current.setLength(0);
                current.append(unit);
            }
        }
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    public static List<String> splitText(String text) {
        return splitText(text, DEFAULT_CHUNK_SIZE);
    }

    public static List<org.springframework.ai.document.Document> createDocuments(
            String text, int chunkSize, Map<String, Object> metadata) {
        return splitText(text, chunkSize).stream()
                .map(chunk -> buildDoc(chunk, metadata))
                .toList();
    }

    public static List<org.springframework.ai.document.Document> createDocuments(
            String text, Map<String, Object> metadata) {
        return createDocuments(text, DEFAULT_CHUNK_SIZE, metadata);
    }

    /**
     * Splits {@code body} into chunks and prepends {@code header} to each chunk —
     * "contextual chunk header" / header propagation pattern. Every chunk
     * becomes self-contained: a downstream embedding/LLM sees the document's
     * identity (title, category, instructor, …) regardless of which chunk was
     * retrieved, eliminating the orphan-chunk problem ({@code "## Title"}
     * alone in one chunk and the value in another) and reducing false
     * adjacency between unrelated fields.
     * <p>
     * If {@code header} is blank, behaves exactly like
     * {@link #createDocuments(String, int, Map)}. If {@code body} is blank,
     * the header alone becomes a single chunk so the document is still
     * findable by its identity.
     *
     * @param header    the contextual block to prepend to every chunk (e.g.
     *                  {@code "# Title\nCategory: Foo\n..."}). Already
     *                  HTML-stripped and normalized.
     * @param body      the long-form content to chunk (already HTML-stripped).
     * @param chunkSize maximum characters per chunk (header counted in).
     * @param metadata  metadata propagated to every produced Document.
     */
    public static List<org.springframework.ai.document.Document> createDocumentsWithHeader(
            String header, String body, int chunkSize, Map<String, Object> metadata) {
        return createDocumentsWithHeader(header, body, chunkSize, metadata, null);
    }

    /**
     * Same as {@link #createDocumentsWithHeader(String, String, int, Map)} but
     * assigns deterministic {@code Document.id} values of the form
     * {@code {idPrefix}-{chunkIndex}} (e.g. {@code "doc-1-0"},
     * {@code "doc-1-1"}). This lets the underlying vector store dedupe by id
     * on re-index — the Lucene store, for example, calls
     * {@code IndexWriter.updateDocument(new Term("id", chunkId), ...)} which
     * atomically replaces the prior chunk with the same id, so reindexing the
     * same source content does not duplicate embeddings.
     * <p>
     * Pass {@code null} or blank to fall back to Spring AI's default
     * UUID-based ids (the prior behavior).
     *
     * @since 2026.2.4
     */
    public static List<org.springframework.ai.document.Document> createDocumentsWithHeader(
            String header, String body, int chunkSize, Map<String, Object> metadata,
            String idPrefix) {
        String normalizedHeader = normalizeWhitespace(header == null ? "" : header);
        String normalizedBody = normalizeWhitespace(body == null ? "" : body);

        if (normalizedHeader.isBlank() && normalizedBody.isBlank()) {
            return List.of();
        }
        if (normalizedHeader.isBlank()) {
            return createDocuments(normalizedBody, chunkSize, metadata, idPrefix);
        }
        if (normalizedBody.isBlank()) {
            return List.of(buildDoc(chunkId(idPrefix, 0), normalizedHeader, metadata));
        }

        // Reserve space for header + separator on every chunk; never go below a
        // sane floor (256 chars) for the body slice — pathological input where
        // header > chunkSize would otherwise produce zero-length body chunks.
        String separator = "\n---\n";
        int reserved = normalizedHeader.length() + separator.length();
        int bodyChunkSize = Math.max(256, chunkSize - reserved);

        List<String> bodyChunks = splitText(normalizedBody, bodyChunkSize);
        List<org.springframework.ai.document.Document> docs = new ArrayList<>(bodyChunks.size());
        for (int i = 0; i < bodyChunks.size(); i++) {
            String fullChunk = normalizedHeader + separator + bodyChunks.get(i);
            docs.add(buildDoc(chunkId(idPrefix, i), fullChunk, metadata));
        }
        return docs;
    }

    public static List<org.springframework.ai.document.Document> createDocumentsWithHeader(
            String header, String body, Map<String, Object> metadata) {
        return createDocumentsWithHeader(header, body, DEFAULT_CHUNK_SIZE, metadata, null);
    }

    public static List<org.springframework.ai.document.Document> createDocumentsWithHeader(
            String header, String body, Map<String, Object> metadata, String idPrefix) {
        return createDocumentsWithHeader(header, body, DEFAULT_CHUNK_SIZE, metadata, idPrefix);
    }

    private static List<org.springframework.ai.document.Document> createDocuments(
            String text, int chunkSize, Map<String, Object> metadata, String idPrefix) {
        List<String> chunks = splitText(text, chunkSize);
        List<org.springframework.ai.document.Document> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            docs.add(buildDoc(chunkId(idPrefix, i), chunks.get(i), metadata));
        }
        return docs;
    }

    private static String chunkId(String idPrefix, int index) {
        if (idPrefix == null || idPrefix.isBlank()) {
            return null;
        }
        return idPrefix + "-" + index;
    }

    public static String truncateText(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    private static org.springframework.ai.document.Document buildDoc(
            String chunk, Map<String, Object> metadata) {
        return buildDoc(null, chunk, metadata);
    }

    /**
     * @param id deterministic id (e.g. {@code "doc-1-0"}) for dedup-on-add via
     *           Lucene's {@code updateDocument}. Pass {@code null} to fall
     *           back to the Spring AI default UUID id.
     */
    private static org.springframework.ai.document.Document buildDoc(
            String id, String chunk, Map<String, Object> metadata) {
        Builder builder = org.springframework.ai.document.Document.builder().text(chunk);
        if (id != null && !id.isBlank()) {
            builder.id(id);
        }
        if (metadata != null) {
            builder.metadata(metadata);
        }
        return builder.build();
    }

    /**
     * Decomposes the input into atomic semantic units, in order: paragraphs,
     * then sentences within oversized paragraphs, then word-aware slices for
     * sentences that themselves exceed the chunk size.
     */
    private static List<String> splitIntoUnits(String text, int chunkSize) {
        List<String> units = new ArrayList<>();
        for (String paragraph : PARAGRAPH_SPLIT.split(text)) {
            String p = paragraph.trim();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() <= chunkSize) {
                units.add(p);
                continue;
            }
            for (String sentence : SENTENCE_SPLIT.split(p)) {
                String s = sentence.trim();
                if (s.isEmpty()) {
                    continue;
                }
                if (s.length() <= chunkSize) {
                    units.add(s);
                } else {
                    units.addAll(wordAwareSplit(s, chunkSize));
                }
            }
        }
        return units;
    }

    /**
     * Splits a too-long single sentence into chunks <= {@code chunkSize} by
     * walking word boundaries. Falls back to a hard split only for tokens
     * that are themselves longer than the chunk size (very rare — long URLs,
     * base64 blobs).
     */
    private static List<String> wordAwareSplit(String sentence, int chunkSize) {
        List<String> parts = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        for (String word : sentence.split("\\s+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (word.length() > chunkSize) {
                if (buf.length() > 0) {
                    parts.add(buf.toString().trim());
                    buf.setLength(0);
                }
                for (int i = 0; i < word.length(); i += chunkSize) {
                    parts.add(word.substring(i, Math.min(word.length(), i + chunkSize)));
                }
                continue;
            }
            if (buf.length() == 0) {
                buf.append(word);
            } else if (buf.length() + 1 + word.length() <= chunkSize) {
                buf.append(' ').append(word);
            } else {
                parts.add(buf.toString().trim());
                buf.setLength(0);
                buf.append(word);
            }
        }
        if (buf.length() > 0) {
            parts.add(buf.toString().trim());
        }
        return parts;
    }
}
