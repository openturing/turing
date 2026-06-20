package com.viglet.turing.sn.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.lucene.search.Query;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Exercises {@link TurDslLuceneExecutor#translateQuery(TurDslQuery)} across
 * every supported {@link TurDslQuery} family so the (large) translation switch
 * is covered. Each type must translate to a non-null Lucene {@link Query}
 * (engine-unsupported families fall back rather than throw).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
class TurDslLuceneExecutorQueryTranslationTest {

    private final TurDslLuceneExecutor executor = new TurDslLuceneExecutor(null, null, null);

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.viglet.turing.sn.dsl.TurDslQueryFixtures#named")
    void translatesEveryQueryTypeToNonNullLuceneQuery(TurDslQuery query) {
        Query translated = executor.translateQuery(query);
        assertThat(translated).as("translation of %s", query.getClass().getSimpleName()).isNotNull();
        assertThat(translated.toString()).isNotNull();
    }
}
