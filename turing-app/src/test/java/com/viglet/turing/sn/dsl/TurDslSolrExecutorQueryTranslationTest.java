package com.viglet.turing.sn.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.solr.client.solrj.request.SolrQuery;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exercises {@link TurDslSolrExecutor#translateQuery(TurDslQuery)} across every
 * supported {@link TurDslQuery} family. Translation only builds a
 * {@link SolrQuery} string, so it needs no running Solr — covers the large
 * clause-translation switch in-process.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurDslSolrExecutorQueryTranslationTest {

    private final TurDslSolrExecutor executor = new TurDslSolrExecutor(null);

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.viglet.turing.sn.dsl.TurDslQueryFixtures#named")
    void translatesEveryQueryTypeToSolrQuery(TurDslQuery query) {
        SolrQuery solrQuery = executor.translateQuery(query);
        assertThat(solrQuery).as("translation of %s", query.getClass().getSimpleName()).isNotNull();
        assertThat(solrQuery.getQuery()).isNotBlank();
    }
}
