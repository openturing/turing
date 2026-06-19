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
    void splitTextShouldSplitWordAwareWhenSingleTokenExceedsChunkSize() {
        // Single 10-char token > chunkSize 3 falls back to hard split.
        String text = "abcdefghij";
        List<String> result = TurRagUtils.splitText(text, 3);
        assertThat(result).containsExactly("abc", "def", "ghi", "j");
    }

    @Test
    void splitTextShouldNotCutWordsInTheMiddle() {
        String text = "Hello World";
        List<String> result = TurRagUtils.splitText(text, 5);
        assertThat(result).containsExactly("Hello", "World");
    }

    @Test
    void splitTextShouldRespectParagraphBoundaries() {
        String text = "First paragraph here.\n\nSecond paragraph here.\n\nThird paragraph here.";
        List<String> result = TurRagUtils.splitText(text, 25);
        assertThat(result).hasSize(3);
        assertThat(result.get(0)).contains("First paragraph");
        assertThat(result.get(1)).contains("Second paragraph");
        assertThat(result.get(2)).contains("Third paragraph");
    }

    @Test
    void splitTextShouldRespectSentenceBoundariesWithinLargeParagraph() {
        String text = "This is sentence one. This is sentence two. This is sentence three.";
        List<String> result = TurRagUtils.splitText(text, 30);
        assertThat(result).isNotEmpty();
        // No chunk ever ends mid-sentence (always ends with sentence-final punctuation
        // or matches the input tail).
        for (String chunk : result) {
            assertThat(chunk.endsWith(".") || text.endsWith(chunk)).isTrue();
        }
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
        // Single 6-char token > chunkSize 3 → word-aware split falls back to hard cut.
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
        List<Document> docs = TurRagUtils.createDocuments("Hello World Foo Bar", 9, metadata);
        assertThat(docs).isNotEmpty();
        assertThat(docs.get(0).getMetadata()).containsEntry("source", "test");
        // No chunk should split a word in the middle.
        for (Document doc : docs) {
            assertThat(doc.getText()).doesNotMatch("(?s).*[A-Za-z]\\Z(?=[A-Za-z])");
        }
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
    void stripHtmlShouldReturnPlainTextUnchanged() {
        assertThat(TurRagUtils.stripHtml("Just plain text, no markup."))
                .isEqualTo("Just plain text, no markup.");
    }

    @Test
    void stripHtmlShouldHandleNullAndBlank() {
        assertThat(TurRagUtils.stripHtml(null)).isEmpty();
        assertThat(TurRagUtils.stripHtml("")).isEmpty();
        assertThat(TurRagUtils.stripHtml("   ")).isEqualTo("   ");
    }

    @Test
    void stripHtmlShouldRemoveTagsAndDecodeEntities() {
        String html = "<p>Hello <strong>world</strong>&nbsp;&amp; goodbye</p>";
        String result = TurRagUtils.stripHtml(html);
        assertThat(result)
                .contains("Hello world")
                .contains("& goodbye")
                .doesNotContain("<")
                .doesNotContain(">")
                .doesNotContain("&nbsp;")
                .doesNotContain("&amp;");
    }

    @Test
    void stripHtmlShouldPreserveParagraphBreaks() {
        String html = "<p>First paragraph.</p><p>Second paragraph.</p>";
        String result = TurRagUtils.stripHtml(html);
        assertThat(result)
                .contains("First paragraph.")
                .contains("Second paragraph.")
                .contains("\n\n");
    }

    @Test
    void stripHtmlShouldNormalizeWhitespace() {
        String html = "<div>Lots\t  of   spaces\n\n\n\nand   newlines</div>";
        String result = TurRagUtils.stripHtml(html);
        assertThat(result)
                .doesNotContain("\t")
                .doesNotContain("    ")
                .doesNotContain("\n\n\n");
    }

    @Test
    void constantsShouldHaveExpectedValues() {
        assertThat(TurRagUtils.DEFAULT_CHUNK_SIZE).isEqualTo(1024);
        assertThat(TurRagUtils.MAX_TEXT_LENGTH).isEqualTo(100_000);
    }

    @Test
    void createDocumentsWithHeaderShouldReturnEmptyWhenBothEmpty() {
        List<Document> docs = TurRagUtils.createDocumentsWithHeader(null, null, Map.of());
        assertThat(docs).isEmpty();
        assertThat(TurRagUtils.createDocumentsWithHeader("", "", Map.of())).isEmpty();
    }

    @Test
    void createDocumentsWithHeaderShouldFallbackToBodyOnlyWhenHeaderBlank() {
        Map<String, Object> meta = Map.of("k", "v");
        List<Document> docs = TurRagUtils.createDocumentsWithHeader("  ", "Plain body text", meta);
        assertThat(docs).hasSize(1);
        assertThat(docs.getFirst().getText()).isEqualTo("Plain body text");
        assertThat(docs.getFirst().getMetadata()).containsEntry("k", "v");
    }

    @Test
    void createDocumentsWithHeaderShouldKeepHeaderOnlyWhenBodyBlank() {
        List<Document> docs = TurRagUtils.createDocumentsWithHeader("# Title\nField: value", "  ", Map.of());
        assertThat(docs).hasSize(1);
        assertThat(docs.getFirst().getText()).contains("# Title");
        assertThat(docs.getFirst().getText()).contains("Field: value");
    }

    @Test
    void createDocumentsWithHeaderShouldPropagateHeaderAcrossEveryChunk() {
        String header = "# MBA Executivo\ncategory: Educação Executiva";
        // Long body with paragraph breaks → multiple chunks at small chunkSize.
        String body = """
                Primeiro parágrafo do conteúdo do curso.

                Segundo parágrafo trata de gestão estratégica.

                Terceiro parágrafo aborda inovação corporativa.""";
        Map<String, Object> meta = Map.of("source_id", "course-1");
        List<Document> docs = TurRagUtils.createDocumentsWithHeader(header, body, 300, meta);

        assertThat(docs).isNotEmpty();
        for (Document doc : docs) {
            assertThat(doc.getText()).contains("# MBA Executivo");
            assertThat(doc.getText()).contains("category: Educação Executiva");
            assertThat(doc.getText()).contains("---");
            assertThat(doc.getMetadata()).containsEntry("source_id", "course-1");
        }
    }

    @Test
    void createDocumentsWithHeaderShouldHandleSmallChunkSizeWithoutInfiniteLoop() {
        String header = "# Title\nField: value";
        String body = "Sentence one. Sentence two. Sentence three. Sentence four.";
        // Header alone is ~26 chars; body chunkSize floored to 256.
        List<Document> docs = TurRagUtils.createDocumentsWithHeader(header, body, 50, Map.of());
        assertThat(docs).hasSize(1);
        assertThat(docs.getFirst().getText()).contains("# Title");
        assertThat(docs.getFirst().getText()).contains("Sentence one");
    }
}
