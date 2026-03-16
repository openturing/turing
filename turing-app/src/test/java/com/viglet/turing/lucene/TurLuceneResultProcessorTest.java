package com.viglet.turing.lucene;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.junit.jupiter.api.Test;

import com.viglet.turing.se.result.TurSEResult;

class TurLuceneResultProcessorTest {

    private final TurLuceneResultProcessor processor = new TurLuceneResultProcessor();

    @Test
    void createTurSEResultShouldMapDocumentFields() {
        Document doc = new Document();
        doc.add(new StringField("id", "doc1", Field.Store.YES));
        doc.add(new StringField("title", "Test Title", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, 1.5f);

        assertEquals("doc1", result.getFields().get("id"));
        assertEquals("Test Title", result.getFields().get("title"));
        assertEquals(1.5f, result.getFields().get(TurLuceneConstants.SCORE));
    }

    @Test
    void createTurSEResultShouldSkipInternalFields() {
        Document doc = new Document();
        doc.add(new StringField("id", "doc1", Field.Store.YES));
        doc.add(new StringField("$facets", "internal", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, 1.0f);

        assertFalse(result.getFields().containsKey("$facets"));
        assertTrue(result.getFields().containsKey("id"));
    }

    @Test
    void createTurSEResultShouldMergeMultiValuedFields() {
        Document doc = new Document();
        doc.add(new StringField("tag", "java", Field.Store.YES));
        doc.add(new StringField("tag", "spring", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, 1.0f);

        Object tagValue = result.getFields().get("tag");
        assertInstanceOf(List.class, tagValue);
        List<?> tags = (List<?>) tagValue;
        assertEquals(2, tags.size());
        assertTrue(tags.contains("java"));
        assertTrue(tags.contains("spring"));
    }

    @Test
    void createTurSEResultShouldHandleNumericFields() {
        Document doc = new Document();
        doc.add(new StoredField("count", 42));

        TurSEResult result = processor.createTurSEResult(doc, 0.8f);

        assertEquals(42, result.getFields().get("count"));
    }

    @Test
    void createTurSEResultShouldHandleEmptyDocument() {
        Document doc = new Document();
        TurSEResult result = processor.createTurSEResult(doc, 0.0f);
        // Only score should be present
        assertEquals(1, result.getFields().size());
        assertEquals(0.0f, result.getFields().get(TurLuceneConstants.SCORE));
    }
}
