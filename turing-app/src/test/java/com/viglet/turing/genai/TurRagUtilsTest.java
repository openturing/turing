package com.viglet.turing.genai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.document.Document;

/**
 * Tests for TurRagUtils.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurRagUtilsTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t", "\n"})
    void splitTextShouldReturnEmptyListForBlankInput(String input) {
        List<String> result = TurRagUtils.splitText(input);
        assertThat(result).isEmpty();
    }

    @Test
    void splitTextShouldReturnSingleChunkWhenTextSmallerThanChunkSize() {
        String text = "Hello world";
        List<String> result = TurRagUtils.splitText(text, 1024);
        assertThat(result).containsExactly("Hello world");
    }

    @Test
    void splitTextShouldReturnSingleChunkWhenTextEqualsChunkSize() {
        String text = "ab";
        List<String> result = TurRagUtils.splitText(text, 2);
        assertThat(result).containsExactly("ab");
    }

    @Test
    void splitTextShouldSplitIntoMultipleChunks() {
        String text = "abcdefghij";
        List<String> result = TurRagUtils.splitText(text, 3);
        assertThat(result).containsExactly("abc", "def", "ghi", "j");
    }

    @Test
    void splitTextShouldUseDefaultChunkSize() {
        String text = "a".repeat(TurRagUtils.DEFAULT_CHUNK_SIZE + 10);
        List<String> result = TurRagUtils.splitText(text);
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).hasSize(TurRagUtils.DEFAULT_CHUNK_SIZE);
        assertThat(result.get(1)).hasSize(10);
    }

    @Test
    void splitTextShouldHandleExactMultipleOfChunkSize() {
        String text = "abcdef";
        List<String> result = TurRagUtils.splitText(text, 3);
        assertThat(result).containsExactly("abc", "def");
    }

    @Test
    void createDocumentsShouldReturnEmptyForNullText() {
        List<Document> docs = TurRagUtils.createDocuments(null, Map.of("key", "value"));
        assertThat(docs).isEmpty();
    }

    @Test
    void createDocumentsShouldReturnEmptyForBlankText() {
        List<Document> docs = TurRagUtils.createDocuments("   ", Map.of("key", "value"));
        assertThat(docs).isEmpty();
    }

    @Test
    void createDocumentsShouldCreateDocumentsWithMetadata() {
        Map<String, Object> metadata = Map.of("source", "test", "type", "unit");
        List<Document> docs = TurRagUtils.createDocuments("Hello World", 5, metadata);
        assertThat(docs).hasSize(3);
        assertThat(docs.get(0).getText()).isEqualTo("Hello");
        assertThat(docs.get(0).getMetadata()).containsEntry("source", "test");
        assertThat(docs.get(1).getText()).isEqualTo(" Worl");
        assertThat(docs.get(2).getText()).isEqualTo("d");
    }

    @Test
    void createDocumentsShouldUseDefaultChunkSize() {
        String text = "Short text";
        List<Document> docs = TurRagUtils.createDocuments(text, Map.of());
        assertThat(docs).hasSize(1);
        assertThat(docs.getFirst().getText()).isEqualTo(text);
    }

    @Test
    void truncateTextShouldReturnNullForNullInput() {
        assertThat(TurRagUtils.truncateText(null, 100)).isNull();
    }

    @Test
    void truncateTextShouldReturnOriginalWhenShorterThanMax() {
        assertThat(TurRagUtils.truncateText("hello", 10)).isEqualTo("hello");
    }

    @Test
    void truncateTextShouldReturnOriginalWhenEqualsMax() {
        assertThat(TurRagUtils.truncateText("hello", 5)).isEqualTo("hello");
    }

    @Test
    void truncateTextShouldTruncateWhenLongerThanMax() {
        assertThat(TurRagUtils.truncateText("hello world", 5)).isEqualTo("hello");
    }

    @Test
    void truncateTextShouldHandleZeroMaxLength() {
        assertThat(TurRagUtils.truncateText("hello", 0)).isEmpty();
    }

    @Test
    void constantsShouldHaveExpectedValues() {
        assertThat(TurRagUtils.DEFAULT_CHUNK_SIZE).isEqualTo(1024);
        assertThat(TurRagUtils.MAX_TEXT_LENGTH).isEqualTo(100_000);
    }
}
