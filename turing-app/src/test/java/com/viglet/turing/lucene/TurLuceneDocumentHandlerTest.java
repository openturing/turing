package com.viglet.turing.lucene;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.MMapDirectory;
import org.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.sn.field.TurSNSiteFieldService;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for TurLuceneDocumentHandler.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurLuceneDocumentHandlerTest {

    @TempDir
    Path tempDir;

    @Mock
    private TurSNSiteFieldService turSNSiteFieldService;

    @Mock
    private TurSNSite turSNSite;

    private TurLuceneDocumentHandler handler;
    private TurLuceneInstance instance;
    private MMapDirectory directory;
    private IndexWriter writer;

    @BeforeEach
    void setUp() throws IOException {
        handler = new TurLuceneDocumentHandler(turSNSiteFieldService, null);
        directory = new MMapDirectory(tempDir);
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        writer = new IndexWriter(directory, config);
        instance = mock(TurLuceneInstance.class);
        lenient().when(instance.getWriter()).thenReturn(writer);
    }

    @Test
    void indexingShouldRemoveScoreVersionAndBoostFields() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("title", TurSNSiteField.builder().name("title").type(TurSEFieldType.TEXT).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc1");
        attrs.put("title", "Hello World");
        attrs.put("score", 1.0f);
        attrs.put("_version_", 3L);
        attrs.put("boost", 2.5);

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(new FieldExistsQuery("id"), 10);
        assertThat(topDocs.totalHits.value()).isEqualTo(1);

        Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
        assertThat(doc.get("title")).isEqualTo("Hello World");
        reader.close();
    }

    @Test
    void indexingShouldHandleStringFieldType() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("category", TurSNSiteField.builder().name("category")
                .type(TurSEFieldType.STRING).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc2");
        attrs.put("category", "books");

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(new FieldExistsQuery("id"), 10);
        assertThat(topDocs.totalHits.value()).isEqualTo(1);

        Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
        assertThat(doc.get("category")).isEqualTo("books");
        reader.close();
    }

    @Test
    void indexingShouldHandleIntFieldType() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("count", TurSNSiteField.builder().name("count")
                .type(TurSEFieldType.INT).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc3");
        attrs.put("count", "42");

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isEqualTo(1);
        reader.close();
    }

    @Test
    void indexingShouldHandleInvalidIntGracefully() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("count", TurSNSiteField.builder().name("count")
                .type(TurSEFieldType.INT).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc4");
        attrs.put("count", "not-a-number");

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isEqualTo(1);
        reader.close();
    }

    @Test
    void indexingShouldHandleLongFieldType() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("timestamp", TurSNSiteField.builder().name("timestamp")
                .type(TurSEFieldType.LONG).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc5");
        attrs.put("timestamp", "9999999999");

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isEqualTo(1);
        reader.close();
    }

    @Test
    void indexingShouldHandleFloatFieldType() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("weight", TurSNSiteField.builder().name("weight")
                .type(TurSEFieldType.FLOAT).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc6");
        attrs.put("weight", "3.14");

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isEqualTo(1);
        reader.close();
    }

    @Test
    void indexingShouldHandleDoubleAndCurrencyFieldTypes() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("amount", TurSNSiteField.builder().name("amount")
                .type(TurSEFieldType.DOUBLE).multiValued(0).build());
        fieldMap.put("price", TurSNSiteField.builder().name("price")
                .type(TurSEFieldType.CURRENCY).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc7");
        attrs.put("amount", "99.99");
        attrs.put("price", "150.00,BRL");

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isEqualTo(1);
        reader.close();
    }

    @Test
    void indexingShouldHandleDateFieldType() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("created", TurSNSiteField.builder().name("created")
                .type(TurSEFieldType.DATE).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc8");
        attrs.put("created", "2026-01-15T10:30:00Z");

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isEqualTo(1);
        reader.close();
    }

    @Test
    void indexingShouldHandleInvalidDateGracefully() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("created", TurSNSiteField.builder().name("created")
                .type(TurSEFieldType.DATE).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc9");
        attrs.put("created", "not-a-date");

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isEqualTo(1);
        reader.close();
    }

    @Test
    void indexingShouldHandleJsonArrayMultiValued() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("tags", TurSNSiteField.builder().name("tags")
                .type(TurSEFieldType.STRING).multiValued(1).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc10");
        attrs.put("tags", new JSONArray(List.of("java", "spring")));

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(new FieldExistsQuery("id"), 10);
        Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
        String[] tagValues = doc.getValues("tags");
        assertThat(tagValues).containsExactly("java", "spring");
        reader.close();
    }

    @Test
    void indexingShouldHandleJsonArraySingleValued() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("name", TurSNSiteField.builder().name("name")
                .type(TurSEFieldType.STRING).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc11");
        attrs.put("name", new JSONArray(List.of("first", "second")));

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(new FieldExistsQuery("id"), 10);
        Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
        // Single-valued: only first element
        assertThat(doc.get("name")).isEqualTo("first");
        reader.close();
    }

    @Test
    void indexingShouldHandleArrayListMultiValued() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("authors", TurSNSiteField.builder().name("authors")
                .type(TurSEFieldType.STRING).multiValued(1).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc12");
        attrs.put("authors", new ArrayList<>(List.of("Alice", "Bob")));

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(new FieldExistsQuery("id"), 10);
        Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
        String[] authorValues = doc.getValues("authors");
        assertThat(authorValues).containsExactly("Alice", "Bob");
        reader.close();
    }

    @Test
    void indexingShouldHandleTuringEntityPrefix() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc13");
        attrs.put("turing_entity_person", new ArrayList<>(List.of("John", "Jane")));

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(new FieldExistsQuery("id"), 10);
        Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
        String[] personValues = doc.getValues("turing_entity_person");
        assertThat(personValues).containsExactly("John", "Jane");
        reader.close();
    }

    @Test
    void indexingShouldSkipNullValues() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc14");
        attrs.put("nullfield", null);

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isEqualTo(1);
        reader.close();
    }

    @Test
    void indexingShouldSkipEmptyStringValues() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("desc", TurSNSiteField.builder().name("desc")
                .type(TurSEFieldType.TEXT).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc15");
        attrs.put("desc", "  ");

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isEqualTo(1);
        reader.close();
    }

    @Test
    void indexingShouldHandleEmptyJsonArray() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("tags", TurSNSiteField.builder().name("tags")
                .type(TurSEFieldType.STRING).multiValued(1).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc16");
        attrs.put("tags", new JSONArray());

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isEqualTo(1);
        reader.close();
    }

    @Test
    void indexingShouldHandleEmptyArrayList() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("tags", TurSNSiteField.builder().name("tags")
                .type(TurSEFieldType.STRING).multiValued(1).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc17");
        attrs.put("tags", new ArrayList<>());

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isEqualTo(1);
        reader.close();
    }

    @Test
    void indexingShouldGenerateIdWhenMissing() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("title", TurSNSiteField.builder().name("title")
                .type(TurSEFieldType.TEXT).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("title", "No ID Document");

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isEqualTo(1);
        reader.close();
    }

    @Test
    void indexingShouldUpdateExistingDocumentWithSameId() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("title", TurSNSiteField.builder().name("title")
                .type(TurSEFieldType.TEXT).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs1 = new LinkedHashMap<>();
        attrs1.put("id", "sameId");
        attrs1.put("title", "Original");
        handler.indexing(instance, turSNSite, attrs1);

        Map<String, Object> attrs2 = new LinkedHashMap<>();
        attrs2.put("id", "sameId");
        attrs2.put("title", "Updated");
        handler.indexing(instance, turSNSite, attrs2);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isEqualTo(1);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(new FieldExistsQuery("id"), 10);
        Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
        assertThat(doc.get("title")).isEqualTo("Updated");
        reader.close();
    }

    @Test
    void deIndexingShouldRemoveDocumentById() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("title", TurSNSiteField.builder().name("title")
                .type(TurSEFieldType.TEXT).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "toDelete");
        attrs.put("title", "Delete Me");
        handler.indexing(instance, turSNSite, attrs);

        handler.deIndexing(instance, "toDelete");

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isZero();
        reader.close();
    }

    @Test
    void deIndexingByTypeShouldRemoveDocumentsByType() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("type", TurSNSiteField.builder().name("type")
                .type(TurSEFieldType.STRING).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "typed1");
        attrs.put("type", "article");
        handler.indexing(instance, turSNSite, attrs);

        handler.deIndexingByType(instance, "article");

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isZero();
        reader.close();
    }

    @Test
    void indexingShouldHandleBoolFieldType() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("active", TurSNSiteField.builder().name("active")
                .type(TurSEFieldType.BOOL).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc18");
        attrs.put("active", "true");

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(new FieldExistsQuery("id"), 10);
        Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
        assertThat(doc.get("active")).isEqualTo("true");
        reader.close();
    }

    @Test
    void indexingShouldHandleArrayFieldType() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("items", TurSNSiteField.builder().name("items")
                .type(TurSEFieldType.ARRAY).multiValued(1).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc19");
        attrs.put("items", new ArrayList<>(List.of("a", "b", "c")));

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(new FieldExistsQuery("id"), 10);
        Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
        String[] values = doc.getValues("items");
        assertThat(values).containsExactly("a", "b", "c");
        reader.close();
    }

    @Test
    void indexingShouldDefaultToStringTypeForUnknownField() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        // No entry for "unknown" field
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc20");
        attrs.put("unknown", "somevalue");

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs topDocs = searcher.search(new FieldExistsQuery("id"), 10);
        Document doc = searcher.storedFields().document(topDocs.scoreDocs[0].doc);
        assertThat(doc.get("unknown")).isEqualTo("somevalue");
        reader.close();
    }

    @Test
    void indexingShouldTruncateLongFacetValues() throws IOException {
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("desc", TurSNSiteField.builder().name("desc")
                .type(TurSEFieldType.TEXT).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        String longValue = "x".repeat(1000);
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc21");
        attrs.put("desc", longValue);

        handler.indexing(instance, turSNSite, attrs);
        writer.commit();

        DirectoryReader reader = DirectoryReader.open(directory);
        assertThat(reader.numDocs()).isEqualTo(1);
        reader.close();
    }

    @Test
    void indexingShouldPropagateIoFailureInsteadOfSwallowing() throws IOException {
        // Regression guard: a swallowed IOException used to falsely report the document
        // as "Indexed" while it was actually lost. It must now surface to the caller so
        // the resilience layer / circuit breaker can react.
        Map<String, TurSNSiteField> fieldMap = new HashMap<>();
        fieldMap.put("title", TurSNSiteField.builder().name("title")
                .type(TurSEFieldType.TEXT).multiValued(0).build());
        when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

        IndexWriter failingWriter = mock(IndexWriter.class);
        when(failingWriter.updateDocument(any(), any())).thenThrow(new IOException("disk full"));
        TurLuceneInstance failingInstance = mock(TurLuceneInstance.class);
        when(failingInstance.getWriter()).thenReturn(failingWriter);

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("id", "doc-io-fail");
        attrs.put("title", "Hello");

        assertThrows(java.io.UncheckedIOException.class,
                () -> handler.indexing(failingInstance, turSNSite, attrs));
    }

    @Test
    void deIndexingShouldPropagateIoFailureInsteadOfSwallowing() throws IOException {
        IndexWriter failingWriter = mock(IndexWriter.class);
        when(failingWriter.deleteDocuments(any(org.apache.lucene.index.Term.class)))
                .thenThrow(new IOException("disk full"));
        TurLuceneInstance failingInstance = mock(TurLuceneInstance.class);
        when(failingInstance.getWriter()).thenReturn(failingWriter);

        assertThrows(java.io.UncheckedIOException.class,
                () -> handler.deIndexing(failingInstance, "doc-io-fail"));
    }

    @Test
    void indexingShouldThrowNpeForNullAttributes() {
        // The cleaned map is a new LinkedHashMap(null) which throws NPE.
        // Use a separate handler/instance to avoid unnecessary stubbing from setUp.
        TurSNSiteFieldService localFieldService = mock(TurSNSiteFieldService.class);
        TurLuceneDocumentHandler localHandler = new TurLuceneDocumentHandler(localFieldService, null);
        TurLuceneInstance localInstance = mock(TurLuceneInstance.class);

        assertThrows(NullPointerException.class,
                () -> localHandler.indexing(localInstance, turSNSite, null));
    }
}
