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
package com.viglet.core.content;

import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Entities;
import org.jsoup.parser.Parser;
import org.jsoup.safety.Safelist;

/**
 * jsoup-based HTML-to-text helpers shared across Viglet products. Two flavors:
 * a plain {@link #toPlainText(String)} (collapse all markup) and a
 * structure-aware {@link #stripHtml(String)} that turns block-level elements
 * into paragraph breaks so downstream chunkers can split on semantic
 * boundaries.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public final class VigletHtmlText {

    private static final Pattern HTML_TAG_HINT = Pattern.compile("<\\s*[a-zA-Z!/][^>]*>");
    private static final Pattern WHITESPACE = Pattern.compile("[ \\t\\x0B\\f]+");
    private static final Pattern MULTI_NEWLINE = Pattern.compile("\\n{3,}");

    private VigletHtmlText() {
        throw new IllegalStateException("Viglet HTML text utility class");
    }

    /**
     * Collapses all markup to its text content. Returns {@code ""} for a
     * {@code null} input.
     */
    public static String toPlainText(String html) {
        if (html == null) {
            return "";
        }
        return Jsoup.parse(html).text();
    }

    /**
     * Strips HTML markup from {@code text} when it looks like HTML, decoding
     * entities and normalizing whitespace. Block-level elements (p, div, li,
     * h1-h6, br, ...) become paragraph breaks (\n\n) so a chunker can split
     * along semantic boundaries afterwards.
     *
     * <p>Returns the original input unchanged when no markup is detected, so
     * plain-text fields incur no overhead.</p>
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
        cleaned = Parser.unescapeEntities(cleaned, false);
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
}
