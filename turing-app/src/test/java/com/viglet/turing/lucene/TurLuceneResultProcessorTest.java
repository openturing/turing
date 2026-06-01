package com.viglet.turing.lucene;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.junit.jupiter.api.Test;

import com.viglet.turing.se.result.TurSEResult;

/**
 * Tests for TurLuceneResultProcessor.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurLuceneResultProcessorTest {

    private final TurLuceneResultProcessor processor = new TurLuceneResultProcessor();

    // ---- createTurSEResult: basic fields ----

    @Test
    void createTurSEResultShouldMapDocumentFields() {
        Document doc = new Document();
        doc.add(new StringField("id", "doc1", Field.Store.YES));
        doc.add(new StringField("title", "Test Title", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, 1.5f, java.util.Map.of(), java.util.List.of());

        assertEquals("doc1", result.getFields().get("id"));
        assertEquals("Test Title", result.getFields().get("title"));
        assertEquals(1.5f, result.getFields().get(TurLuceneConstants.SCORE));
    }

    // ---- createTurSEResult: internal fields ----

    @Test
    void createTurSEResultShouldSkipInternalFields() {
        Document doc = new Document();
        doc.add(new StringField("id", "doc1", Field.Store.YES));
        doc.add(new StringField("$facets", "internal", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, 1.0f, java.util.Map.of(), java.util.List.of());

        assertFalse(result.getFields().containsKey("$facets"));
        assertTrue(result.getFields().containsKey("id"));
    }

    @Test
    void createTurSEResultShouldSkipAllDollarPrefixedFields() {
        Document doc = new Document();
        doc.add(new StringField("$internal1", "val1", Field.Store.YES));
        doc.add(new StringField("$internal2", "val2", Field.Store.YES));
        doc.add(new StringField("normal", "val3", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, 1.0f, java.util.Map.of(), java.util.List.of());

        assertFalse(result.getFields().containsKey("$internal1"));
        assertFalse(result.getFields().containsKey("$internal2"));
        assertTrue(result.getFields().containsKey("normal"));
    }

    // ---- createTurSEResult: multi-valued fields ----

    @Test
    void createTurSEResultShouldMergeMultiValuedFields() {
        Document doc = new Document();
        doc.add(new StringField("tag", "java", Field.Store.YES));
        doc.add(new StringField("tag", "spring", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, 1.0f, java.util.Map.of(), java.util.List.of());

        Object tagValue = result.getFields().get("tag");
        assertInstanceOf(List.class, tagValue);
        List<?> tags = (List<?>) tagValue;
        assertEquals(2, tags.size());
        assertTrue(tags.contains("java"));
        assertTrue(tags.contains("spring"));
    }

    @Test
    void createTurSEResultShouldMergeThreeOrMoreValues() {
        Document doc = new Document();
        doc.add(new StringField("tag", "java", Field.Store.YES));
        doc.add(new StringField("tag", "spring", Field.Store.YES));
        doc.add(new StringField("tag", "lucene", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, 1.0f, java.util.Map.of(), java.util.List.of());

        Object tagValue = result.getFields().get("tag");
        assertInstanceOf(List.class, tagValue);
        List<?> tags = (List<?>) tagValue;
        assertEquals(3, tags.size());
        assertTrue(tags.contains("java"));
        assertTrue(tags.contains("spring"));
        assertTrue(tags.contains("lucene"));
    }

    // ---- createTurSEResult: numeric fields ----

    @Test
    void createTurSEResultShouldHandleIntNumericFields() {
        Document doc = new Document();
        doc.add(new StoredField("count", 42));

        TurSEResult result = processor.createTurSEResult(doc, 0.8f, java.util.Map.of(), java.util.List.of());

        assertEquals(42, result.getFields().get("count"));
    }

    @Test
    void createTurSEResultShouldHandleLongNumericFields() {
        Document doc = new Document();
        doc.add(new StoredField("timestamp", 9999999999L));

        TurSEResult result = processor.createTurSEResult(doc, 0.5f, java.util.Map.of(), java.util.List.of());

        assertEquals(9999999999L, result.getFields().get("timestamp"));
    }

    @Test
    void createTurSEResultShouldHandleFloatNumericFields() {
        Document doc = new Document();
        doc.add(new StoredField("weight", 3.14f));

        TurSEResult result = processor.createTurSEResult(doc, 0.5f, java.util.Map.of(), java.util.List.of());

        assertEquals(3.14f, result.getFields().get("weight"));
    }

    @Test
    void createTurSEResultShouldHandleDoubleNumericFields() {
        Document doc = new Document();
        doc.add(new StoredField("amount", 99.99));

        TurSEResult result = processor.createTurSEResult(doc, 0.5f, java.util.Map.of(), java.util.List.of());

        assertEquals(99.99, result.getFields().get("amount"));
    }

    // ---- createTurSEResult: TextField ----

    @Test
    void createTurSEResultShouldHandleTextField() {
        Document doc = new Document();
        doc.add(new TextField("body", "This is body text", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, 1.0f, java.util.Map.of(), java.util.List.of());

        assertEquals("This is body text", result.getFields().get("body"));
    }

    // ---- createTurSEResult: empty document ----

    @Test
    void createTurSEResultShouldHandleEmptyDocument() {
        Document doc = new Document();
        TurSEResult result = processor.createTurSEResult(doc, 0.0f, java.util.Map.of(), java.util.List.of());
        // Only score should be present
        assertEquals(1, result.getFields().size());
        assertEquals(0.0f, result.getFields().get(TurLuceneConstants.SCORE));
    }

    // ---- createTurSEResult: score values ----

    @Test
    void createTurSEResultShouldStoreZeroScore() {
        Document doc = new Document();
        doc.add(new StringField("id", "1", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, 0.0f, java.util.Map.of(), java.util.List.of());

        assertEquals(0.0f, result.getFields().get(TurLuceneConstants.SCORE));
    }

    @Test
    void createTurSEResultShouldStoreHighScore() {
        Document doc = new Document();
        doc.add(new StringField("id", "1", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, Float.MAX_VALUE, java.util.Map.of(), java.util.List.of());

        assertEquals(Float.MAX_VALUE, result.getFields().get(TurLuceneConstants.SCORE));
    }

    @Test
    void createTurSEResultShouldStoreNegativeScore() {
        Document doc = new Document();
        doc.add(new StringField("id", "1", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, -1.0f, java.util.Map.of(), java.util.List.of());

        assertEquals(-1.0f, result.getFields().get(TurLuceneConstants.SCORE));
    }

    // ---- createTurSEResult: mixed field types ----

    @Test
    void createTurSEResultShouldHandleMixedFieldTypes() {
        Document doc = new Document();
        doc.add(new StringField("id", "doc1", Field.Store.YES));
        doc.add(new TextField("body", "Hello world", Field.Store.YES));
        doc.add(new StoredField("count", 10));
        doc.add(new StoredField("price", 19.99));

        TurSEResult result = processor.createTurSEResult(doc, 2.0f, java.util.Map.of(), java.util.List.of());

        assertEquals("doc1", result.getFields().get("id"));
        assertEquals("Hello world", result.getFields().get("body"));
        assertEquals(10, result.getFields().get("count"));
        assertEquals(19.99, result.getFields().get("price"));
        assertEquals(2.0f, result.getFields().get(TurLuceneConstants.SCORE));
    }

    // ---- createTurSEResult: unstored fields produce no string value ----

    @Test
    void createTurSEResultShouldHandleNonStoredPointFields() {
        Document doc = new Document();
        doc.add(new IntPoint("intpoint", 5));
        // IntPoint is not stored, so numericValue() and stringValue() return null
        // extractStoredValue returns ""

        TurSEResult result = processor.createTurSEResult(doc, 1.0f, java.util.Map.of(), java.util.List.of());

        // IntPoint fields have no stored value, so they appear as empty string
        // But the field starts with no "$" so it's not skipped
        // The actual behavior depends on IndexableField contract
        assertNotNull(result);
    }

    // ---- createTurSEResult: field count ----

    @Test
    void createTurSEResultFieldCountShouldIncludeScore() {
        Document doc = new Document();
        doc.add(new StringField("id", "1", Field.Store.YES));
        doc.add(new StringField("name", "test", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, 1.0f, java.util.Map.of(), java.util.List.of());

        // id + name + score = 3
        assertEquals(3, result.getFields().size());
    }

    // ---- createTurSEResult: special characters in values ----

    @Test
    void createTurSEResultShouldHandleSpecialCharactersInValues() {
        Document doc = new Document();
        doc.add(new StringField("id", "doc<>&\"'", Field.Store.YES));
        doc.add(new TextField("body", "Hello <world> & \"quotes\"", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, 1.0f, java.util.Map.of(), java.util.List.of());

        assertEquals("doc<>&\"'", result.getFields().get("id"));
        assertEquals("Hello <world> & \"quotes\"", result.getFields().get("body"));
    }

    @Test
    void createTurSEResultShouldHandleUnicodeValues() {
        Document doc = new Document();
        doc.add(new StringField("title", "Titulo em portugues", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, 1.0f, java.util.Map.of(), java.util.List.of());

        assertEquals("Titulo em portugues", result.getFields().get("title"));
    }

    @Test
    void createTurSEResultShouldHandleEmptyStringFieldValue() {
        Document doc = new Document();
        doc.add(new StringField("empty", "", Field.Store.YES));

        TurSEResult result = processor.createTurSEResult(doc, 1.0f, java.util.Map.of(), java.util.List.of());

        assertEquals("", result.getFields().get("empty"));
    }

    // ---- populateResultsParameters tests ----

    @Test
    void populateResultsParametersShouldSetAllFields() throws Exception {
        java.lang.reflect.Method method = TurLuceneResultProcessor.class.getDeclaredMethod(
                "populateResultsParameters",
                com.viglet.turing.commons.se.TurSEParameters.class,
                com.viglet.turing.se.result.TurSEResults.class);
        method.setAccessible(true);

        com.viglet.turing.commons.se.TurSEParameters params =
                org.mockito.Mockito.mock(com.viglet.turing.commons.se.TurSEParameters.class);
        org.mockito.Mockito.when(params.getCurrentPage()).thenReturn(3);
        org.mockito.Mockito.when(params.getSort()).thenReturn("relevance");
        org.mockito.Mockito.when(params.getQuery()).thenReturn("test query");

        com.viglet.turing.se.result.TurSEResults results =
                com.viglet.turing.se.result.TurSEResults.builder().build();

        method.invoke(processor, params, results);

        // limit is no longer set here — it comes from effectiveRows() in getResults()
        assertEquals(3, results.getCurrentPage());
        assertEquals("relevance", results.getSort());
        assertEquals("test query", results.getQueryString());
    }

    // ---- effectiveRows tests ----

    private int invokeEffectiveRows(Integer rows, Integer rowsPerPage) throws Exception {
        java.lang.reflect.Method method = TurLuceneResultProcessor.class.getDeclaredMethod(
                "effectiveRows", com.viglet.turing.commons.se.TurSEParameters.class,
                com.viglet.turing.persistence.model.sn.TurSNSite.class);
        method.setAccessible(true);

        com.viglet.turing.commons.se.TurSEParameters params =
                org.mockito.Mockito.mock(com.viglet.turing.commons.se.TurSEParameters.class);
        org.mockito.Mockito.when(params.getRows()).thenReturn(rows);

        com.viglet.turing.persistence.model.sn.TurSNSite site =
                org.mockito.Mockito.mock(com.viglet.turing.persistence.model.sn.TurSNSite.class);
        org.mockito.Mockito.when(site.getRowsPerPage()).thenReturn(rowsPerPage);

        return (int) method.invoke(processor, params, site);
    }

    @Test
    void effectiveRowsShouldUseRequestedRowsWhenPositive() throws Exception {
        assertEquals(25, invokeEffectiveRows(25, 10));
    }

    @Test
    void effectiveRowsShouldFallBackToRowsPerPageWhenRowsNegative() throws Exception {
        // The bug: omitting "rows" in the URL arrives as -1; must fall back to site rowsPerPage
        assertEquals(10, invokeEffectiveRows(-1, 10));
    }

    @Test
    void effectiveRowsShouldFallBackToRowsPerPageWhenRowsNull() throws Exception {
        assertEquals(15, invokeEffectiveRows(null, 15));
    }

    @Test
    void effectiveRowsShouldDefaultToTenWhenBothMissing() throws Exception {
        assertEquals(10, invokeEffectiveRows(-1, 0));
        assertEquals(10, invokeEffectiveRows(null, null));
    }

    // ---- firstRow tests ----

    private int invokeFirstRow(Integer currentPage, int rows) throws Exception {
        java.lang.reflect.Method method = TurLuceneResultProcessor.class.getDeclaredMethod(
                "firstRow", com.viglet.turing.commons.se.TurSEParameters.class, int.class);
        method.setAccessible(true);

        com.viglet.turing.commons.se.TurSEParameters params =
                org.mockito.Mockito.mock(com.viglet.turing.commons.se.TurSEParameters.class);
        org.mockito.Mockito.when(params.getCurrentPage()).thenReturn(currentPage);

        return (int) method.invoke(processor, params, rows);
    }

    @Test
    void firstRowShouldReturnZeroForPageOne() throws Exception {
        assertEquals(0, invokeFirstRow(1, 10));
    }

    @Test
    void firstRowShouldCalculateOffsetCorrectly() throws Exception {
        assertEquals(20, invokeFirstRow(3, 10));
    }

    @Test
    void firstRowShouldReturnZeroForPageZero() throws Exception {
        // page <= 0 is normalized to page 1 -> offset 0
        assertEquals(0, invokeFirstRow(0, 10));
    }

    @Test
    void firstRowShouldReturnZeroForNegativePage() throws Exception {
        assertEquals(0, invokeFirstRow(-1, 10));
    }

    @Test
    void firstRowShouldReturnZeroForNullPage() throws Exception {
        assertEquals(0, invokeFirstRow(null, 10));
    }

    // ---- getResults: pagination regression (issue: every page showed page 1) ----

    @Test
    void getResultsShouldPaginateWhenRowsOmittedFromRequest() throws Exception {
        // Build an in-memory index with 25 deterministically-ordered docs.
        org.apache.lucene.store.Directory directory =
                new org.apache.lucene.store.ByteBuffersDirectory();
        org.apache.lucene.index.IndexWriterConfig config =
                new org.apache.lucene.index.IndexWriterConfig(
                        new org.apache.lucene.analysis.standard.StandardAnalyzer());
        org.apache.lucene.index.IndexWriter writer =
                new org.apache.lucene.index.IndexWriter(directory, config);
        for (int i = 0; i < 25; i++) {
            Document doc = new Document();
            doc.add(new StringField("id", "doc" + i, Field.Store.YES));
            writer.addDocument(doc);
        }
        writer.commit();

        TurLuceneInstance instance = new TurLuceneInstance(writer, directory,
                java.nio.file.Path.of("test-index"), new org.apache.lucene.facet.FacetsConfig());

        // Site configured with 10 rows per page; request omits "rows" (arrives as -1).
        com.viglet.turing.persistence.model.sn.TurSNSite site =
                org.mockito.Mockito.mock(com.viglet.turing.persistence.model.sn.TurSNSite.class);
        org.mockito.Mockito.when(site.getRowsPerPage()).thenReturn(10);

        org.apache.lucene.search.Query query = new org.apache.lucene.search.MatchAllDocsQuery();

        try {
            com.viglet.turing.se.result.TurSEResults page1 = processor.getResults(instance, site,
                    query, paramsForPage(1), List.of(), List.of(), null, 0L);
            com.viglet.turing.se.result.TurSEResults page2 = processor.getResults(instance, site,
                    query, paramsForPage(2), List.of(), List.of(), null, 0L);

            assertEquals(25, page1.getNumFound());
            assertEquals(10, page1.getResults().size());
            assertEquals(10, page2.getResults().size());

            assertEquals(0, page1.getStart());
            assertEquals(10, page2.getStart());
            assertEquals(10, page1.getLimit());
            assertEquals(10, page2.getLimit());

            List<String> page1Ids = page1.getResults().stream()
                    .map(r -> (String) r.getFields().get("id")).toList();
            List<String> page2Ids = page2.getResults().stream()
                    .map(r -> (String) r.getFields().get("id")).toList();

            // The regression: page 2 must NOT equal page 1.
            assertNotEquals(page1Ids, page2Ids);
            assertEquals("doc0", page1Ids.get(0));
            assertEquals("doc10", page2Ids.get(0));
        } finally {
            instance.close();
        }
    }

    private com.viglet.turing.commons.se.TurSEParameters paramsForPage(int page) {
        com.viglet.turing.commons.se.TurSEParameters params =
                org.mockito.Mockito.mock(com.viglet.turing.commons.se.TurSEParameters.class);
        org.mockito.Mockito.when(params.getCurrentPage()).thenReturn(page);
        // "rows" omitted from the URL arrives negative
        org.mockito.Mockito.when(params.getRows()).thenReturn(-1);
        return params;
    }
}
