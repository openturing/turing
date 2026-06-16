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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class VigletHtmlTextTest {

    @Test
    void plainTextNullIsEmpty() {
        assertThat(VigletHtmlText.toPlainText(null)).isEmpty();
    }

    @Test
    void plainTextCollapsesMarkup() {
        assertThat(VigletHtmlText.toPlainText("<p>Hello <b>World</b></p>")).isEqualTo("Hello World");
    }

    @Test
    void stripHtmlLeavesPlainTextUntouched() {
        String input = "no markup here";
        assertThat(VigletHtmlText.stripHtml(input)).isEqualTo(input);
    }

    @Test
    void stripHtmlNullBecomesEmpty() {
        assertThat(VigletHtmlText.stripHtml(null)).isEmpty();
    }

    @Test
    void stripHtmlTurnsBlocksIntoParagraphBreaks() {
        String result = VigletHtmlText.stripHtml("<p>First paragraph</p><p>Second paragraph</p>");
        assertThat(result).isEqualTo("First paragraph\n\nSecond paragraph");
    }

    @Test
    void stripHtmlDecodesEntities() {
        assertThat(VigletHtmlText.stripHtml("<span>A &amp; B</span>")).contains("A & B");
    }

    @Test
    void normalizeWhitespaceCollapsesSpacesAndNewlines() {
        String result = VigletHtmlText.normalizeWhitespace("a   b\n\n\n\nc");
        assertThat(result).isEqualTo("a b\n\nc");
    }

    @Test
    void normalizeWhitespaceNullIsEmpty() {
        assertThat(VigletHtmlText.normalizeWhitespace(null)).isEmpty();
    }
}
