package com.viglet.turing.lucene;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;

/**
 * T1064 — ranking expressions reorder results on the embedded Lucene engine.
 */
@ExtendWith(MockitoExtension.class)
class TurLuceneRankingExpressionsTest {

    private static final long NOW = Instant.parse("2026-10-10T15:00:00Z").toEpochMilli();

    @Mock
    private TurSNRankingExpressionRepository expressionRepository;
    @Mock
    private TurSNRankingConditionRepository conditionRepository;
    @Mock
    private TurSNSiteFieldExtRepository fieldExtRepository;

    private final TurSNSite site = new TurSNSite();

    private TurLuceneRankingExpressions rankingExpressions() {
        return new TurLuceneRankingExpressions(expressionRepository, conditionRepository, fieldExtRepository);
    }

    private void fields(Object... nameAndType) {
        List<TurSNSiteFieldExt> fields = new ArrayList<>();
        for (int i = 0; i < nameAndType.length; i += 2) {
            fields.add(TurSNSiteFieldExt.builder().name((String) nameAndType[i])
                    .type((TurSEFieldType) nameAndType[i + 1]).build());
        }
        when(fieldExtRepository.findByTurSNSite(any(), any())).thenReturn(fields);
    }

    private TurSNRankingExpression expression(String name, float weight, String... attributeAndValue) {
        TurSNRankingExpression expression = new TurSNRankingExpression();
        expression.setName(name);
        expression.setWeight(weight);
        Set<TurSNRankingCondition> conditions = new LinkedHashSet<>();
        for (int i = 0; i < attributeAndValue.length; i += 2) {
            TurSNRankingCondition condition = new TurSNRankingCondition();
            condition.setAttribute(attributeAndValue[i]);
            condition.setValue(attributeAndValue[i + 1]);
            conditions.add(condition);
        }
        when(conditionRepository.findByTurSNRankingExpression(expression)).thenReturn(conditions);
        return expression;
    }

    private static Document job(String id, String category, Instant published) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new StringField("category", category, Field.Store.YES));
        doc.add(new TextField("title", "job " + id, Field.Store.YES));
        long ms = published.toEpochMilli();
        doc.add(new LongPoint("publicationDate", ms));
        doc.add(new NumericDocValuesField("publicationDate", ms));
        return doc;
    }

    /** Ids in score order for match-all plus the boosts, the shape TurLuceneQueryBuilder builds. */
    private static List<String> rank(List<Query> boosts) throws Exception {
        Instant now = Instant.ofEpochMilli(NOW);
        try (Directory dir = new ByteBuffersDirectory()) {
            try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
                Document oldIt = job("old-it", "it", now.minus(900, ChronoUnit.DAYS));
                oldIt.add(new LongPoint("salaryMin", 5000));
                oldIt.add(new NumericDocValuesField("salaryMin", 5000));
                writer.addDocument(oldIt);
                writer.addDocument(job("new-sales", "sales", now.minus(1, ChronoUnit.DAYS)));
                writer.addDocument(job("mid-it", "it", now.minus(200, ChronoUnit.DAYS)));
            }
            try (DirectoryReader reader = DirectoryReader.open(dir)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                BooleanQuery.Builder builder = new BooleanQuery.Builder()
                        .add(new MatchAllDocsQuery(), BooleanClause.Occur.MUST);
                boosts.forEach(boost -> builder.add(boost, BooleanClause.Occur.SHOULD));
                List<String> ids = new ArrayList<>();
                for (ScoreDoc hit : searcher.search(builder.build(), 10).scoreDocs) {
                    ids.add(searcher.storedFields().document(hit.doc).get("id"));
                }
                return ids;
            }
        }
    }

    @Test
    void recencyExpressionRanksNewestFirst() throws Exception {
        fields("publicationDate", TurSEFieldType.DATE);
        TurSNRankingExpression recent = expression("recent", 2f, "publicationDate", "asc");
        when(expressionRepository.findByTurSNSite(any(), any())).thenReturn(Set.of(recent));

        List<String> ranked = rank(rankingExpressions().boostQueries(site, NOW));

        assertEquals(List.of("new-sales", "mid-it", "old-it"), ranked);
    }

    @Test
    void valueExpressionOutweighsRecencyWhenHeavier() throws Exception {
        fields("publicationDate", TurSEFieldType.DATE, "category", TurSEFieldType.STRING);
        Set<TurSNRankingExpression> expressions = new LinkedHashSet<>();
        expressions.add(expression("recent", 1f, "publicationDate", "asc"));
        expressions.add(expression("it-first", 10f, "category", "it"));
        when(expressionRepository.findByTurSNSite(any(), any())).thenReturn(expressions);

        List<String> ranked = rank(rankingExpressions().boostQueries(site, NOW));

        // Both IT jobs outrank the newest sales job; recency orders the two IT jobs.
        assertEquals(List.of("mid-it", "old-it", "new-sales"), ranked);
    }

    @Test
    void conditionsWithinOneExpressionMustAllMatch() throws Exception {
        fields("publicationDate", TurSEFieldType.DATE, "category", TurSEFieldType.STRING);
        TurSNRankingExpression recentIt = expression("recent-it", 5f, "category", "it", "publicationDate", "asc");
        when(expressionRepository.findByTurSNSite(any(), any())).thenReturn(Set.of(recentIt));

        List<String> ranked = rank(rankingExpressions().boostQueries(site, NOW));

        assertEquals("mid-it", ranked.getFirst());
        assertEquals("new-sales", ranked.getLast());
    }

    @Test
    void existsConditionBoostsDocumentsThatCarryTheField() throws Exception {
        fields("salaryMin", TurSEFieldType.LONG);
        TurSNRankingExpression hasSalary = new TurSNRankingExpression();
        hasSalary.setName("has-salary");
        hasSalary.setWeight(4f);
        TurSNRankingCondition exists = new TurSNRankingCondition();
        exists.setAttribute("salaryMin");
        exists.setCondition(TurSNRankingCondition.EXISTS);
        when(conditionRepository.findByTurSNRankingExpression(hasSalary)).thenReturn(Set.of(exists));
        when(expressionRepository.findByTurSNSite(any(), any())).thenReturn(Set.of(hasSalary));

        List<String> ranked = rank(rankingExpressions().boostQueries(site, NOW));

        // Only old-it carries salaryMin, so it outranks two newer jobs.
        assertEquals("old-it", ranked.getFirst());
    }

    @Test
    void noExpressionsMeansNoBoosts() {
        when(expressionRepository.findByTurSNSite(any(), any())).thenReturn(Set.of());

        assertTrue(rankingExpressions().boostQueries(site, NOW).isEmpty());
    }

    @Test
    void anExpressionWithoutConditionsOrWithAnUnusableConditionIsSkipped() {
        fields("salary", TurSEFieldType.INT);
        TurSNRankingExpression empty = expression("empty", 3f);
        TurSNRankingExpression badNumber = expression("bad", 3f, "salary", "lots");
        TurSNRankingExpression zeroWeight = expression("zero", 0f, "salary", "10");
        when(expressionRepository.findByTurSNSite(any(), any()))
                .thenReturn(Set.of(empty, badNumber, zeroWeight));

        assertTrue(rankingExpressions().boostQueries(site, NOW).isEmpty());
    }

    @Test
    void recencyPivotMatchesSolrRecipCurve() {
        // recip(x, 3.16e-11, 1, 1) halves at x = 1 / 3.16e-11 ms (about a year).
        assertEquals(1 / 3.16e-11, TurLuceneRankingExpressions.RECENCY_PIVOT_MS, 1);
    }
}
