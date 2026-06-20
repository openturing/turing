package com.viglet.turing.sn.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

/**
 * Exercises {@link TurDslElasticsearchExecutor#translateQuery(TurDslQuery)}
 * across every supported {@link TurDslQuery} family. Building the typed ES
 * {@link Query} object needs no running Elasticsearch — covers the large
 * fluent-builder translation switch (and its extended second tier) in-process.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurDslElasticsearchExecutorQueryTranslationTest {

    private final TurDslElasticsearchExecutor executor = new TurDslElasticsearchExecutor(null);

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.viglet.turing.sn.dsl.TurDslQueryFixtures#named")
    void translatesEveryQueryTypeToEsQuery(TurDslQuery query) {
        if (query instanceof TurDslQuery.GeoShape) {
            // KNOWN GAP: the ES geo_shape branch sets only `field` and never the
            // required `shape`, so the ES client rejects the build. Characterized
            // here (rather than skipped) so the regression is visible if/when the
            // translation is completed.
            assertThatThrownBy(() -> executor.translateQuery(query))
                    .hasMessageContaining("shape");
            return;
        }
        Query esQuery = executor.translateQuery(query);
        assertThat(esQuery).as("translation of %s", query.getClass().getSimpleName()).isNotNull();
    }
}
