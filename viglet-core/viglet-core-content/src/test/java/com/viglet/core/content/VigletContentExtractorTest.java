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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class VigletContentExtractorTest {

    @Test
    void extractMissingFileReturnsNull() {
        assertThat(VigletContentExtractor.extract(new File("does-not-exist-12345.txt"))).isNull();
    }

    @Test
    void extractNullFileReturnsNull() {
        assertThat(VigletContentExtractor.extract((File) null)).isNull();
    }

    @Test
    void extractPlainTextStream() {
        VigletExtractedContent result = VigletContentExtractor
                .extract(new ByteArrayInputStream("Hello Viglet Core".getBytes(StandardCharsets.UTF_8)));

        assertThat(result).isNotNull();
        assertThat(result.file()).isNull();
        assertThat(result.metadata()).isNotNull();
        assertThat(result.content()).contains("Hello Viglet Core");
    }

    @Test
    void extractTextFileBindsFile(@TempDir File tempDir) throws IOException {
        File file = new File(tempDir, "doc.txt");
        Files.writeString(file.toPath(), "Document body content");

        VigletExtractedContent result = VigletContentExtractor.extract(file);

        assertThat(result).isNotNull();
        assertThat(result.file()).isEqualTo(file);
        assertThat(result.content()).contains("Document body content");
    }

    @Test
    void parseDocumentReturnsText() throws IOException {
        Optional<String> text = VigletContentExtractor
                .parseDocument(new ByteArrayInputStream("Embedded text".getBytes(StandardCharsets.UTF_8)));

        assertThat(text).isPresent();
        assertThat(text.get()).contains("Embedded text");
    }
}
