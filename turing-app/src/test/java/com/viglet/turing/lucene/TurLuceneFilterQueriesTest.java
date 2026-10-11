package com.viglet.turing.lucene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchNoDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Tests for {@link TurLuceneFilterQueries} (T1075): a filter query is read by
 * the field's type, so ranges and numeric/date values match the points
 * {@link TurLuceneDocumentHandler} indexes.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
class TurLuceneFilterQueriesTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    private Directory directory;
    private IndexWriter writer;
    private IndexSearcher searcher;
    private DirectoryReader reader;

    @BeforeEach
    void index() throws Exception {
        directory = new ByteBuffersDirectory();
        writer = new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()));
        addJob("today", "2026-10-11T08:00:00Z", 3000, 2500.5, "b");
        addJob("yesterday", "2026-10-10T09:00:00Z", 4000, 4000.0, "c");
        addJob("lastweek", "2026-10-05T10:00:00Z", 5000, 5100.0, "d");
        addJob("lastmonth", "2026-09-01T10:00:00Z", 1500, 1500.0, "a");
        writer.commit();
        reader = DirectoryReader.open(writer);
        searcher = new IndexSearcher(reader);
    }

    @AfterEach
    void close() throws Exception {
        reader.close();
        writer.close();
        directory.close();
    }

    private void addJob(String id, String publishedAt, int salary, double price, String code)
            throws Exception {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new LongPoint("publishedAt", Instant.parse(publishedAt).toEpochMilli()));
        doc.add(new IntPoint("salary", salary));
        doc.add(new DoublePoint("price", price));
        doc.add(new StringField("code", code, Field.Store.YES));
        writer.addDocument(doc);
    }

    private Set<String> ids(String field, String value, TurSEFieldType type) throws Exception {
        Query query = TurLuceneFilterQueries.build(field, value, type, NOW);
        ScoreDoc[] hits = searcher.search(query, 10).scoreDocs;
        return java.util.Arrays.stream(hits)
                .map(hit -> {
                    try {
                        return searcher.storedFields().document(hit.doc).get("id");
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                })
                .collect(Collectors.toSet());
    }

    @Test
    void relativeDateRangeMatchesRecentDocuments() throws Exception {
        assertEquals(Set.of("today", "yesterday"),
                ids("publishedAt", "[NOW-1DAY/DAY TO NOW]", TurSEFieldType.DATE));
        assertEquals(Set.of("today", "yesterday", "lastweek"),
                ids("publishedAt", "[NOW-7DAYS TO *]", TurSEFieldType.DATE));
        assertEquals(Set.of("today"), ids("publishedAt", "[NOW-1DAY TO NOW]", TurSEFieldType.DATE));
    }

    @Test
    void absoluteDateRangeAcceptsInstantsAndDays() throws Exception {
        assertEquals(Set.of("today", "yesterday"),
                ids("publishedAt", "[2026-10-10T00:00:00Z TO *]", TurSEFieldType.DATE));
        assertEquals(Set.of("lastweek", "lastmonth"),
                ids("publishedAt", "[* TO 2026-10-10}", TurSEFieldType.DATE));
    }

    @Test
    void exactDateMatchesTheIndexedInstant() throws Exception {
        assertEquals(Set.of("yesterday"),
                ids("publishedAt", "2026-10-10T09:00:00Z", TurSEFieldType.DATE));
    }

    @Test
    void numericRangeHonoursExclusiveBounds() throws Exception {
        assertEquals(Set.of("yesterday", "lastweek"), ids("salary", "[4000 TO *]", TurSEFieldType.INT));
        assertEquals(Set.of("lastweek"), ids("salary", "{4000 TO *]", TurSEFieldType.INT));
        assertEquals(Set.of("today", "lastmonth"), ids("price", "[* TO 4000.0}", TurSEFieldType.DOUBLE));
    }

    @Test
    void exactNumberMatchesThePoint() throws Exception {
        assertEquals(Set.of("today"), ids("salary", "3000", TurSEFieldType.INT));
        assertEquals(Set.of("today"), ids("price", "2500.5", TurSEFieldType.DOUBLE));
    }

    @Test
    void stringFieldsKeepExactTermsAndAcceptTermRanges() throws Exception {
        assertEquals(Set.of("yesterday"), ids("code", "c", TurSEFieldType.STRING));
        assertEquals(Set.of("today", "yesterday"), ids("code", "[b TO c]", TurSEFieldType.STRING));
    }

    @Test
    void unknownFieldTypeFallsBackToTermLookup() {
        assertInstanceOf(TermQuery.class, TurLuceneFilterQueries.build("code", "c", null, NOW));
    }

    @Test
    void unparseableRangeMatchesNothing() {
        assertInstanceOf(MatchNoDocsQuery.class,
                TurLuceneFilterQueries.build("salary", "[lots TO more]", TurSEFieldType.INT, NOW));
        assertInstanceOf(MatchNoDocsQuery.class,
                TurLuceneFilterQueries.build("publishedAt", "[NOW-3FORTNIGHTS TO NOW]",
                        TurSEFieldType.DATE, NOW));
    }

    @Test
    void dateMathRoundsAndSteps() {
        assertEquals(Instant.parse("2026-10-04T00:00:00Z"),
                TurLuceneFilterQueries.parseDate("NOW/DAY-7DAYS", NOW));
        assertEquals(Instant.parse("2026-10-01T00:00:00Z"),
                TurLuceneFilterQueries.parseDate("NOW/MONTH", NOW));
        assertEquals(Instant.parse("2026-10-09T00:00:00Z"),
                TurLuceneFilterQueries.parseDate("2026-10-10T00:00:00Z-1DAY", NOW));
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"),
                TurLuceneFilterQueries.parseDate("2026-05-20/YEAR", NOW));
    }
}
