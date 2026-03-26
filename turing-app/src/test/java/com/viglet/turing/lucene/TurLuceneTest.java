package com.viglet.turing.lucene;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TurLuceneTest {

    @ParameterizedTest
    @ValueSource(strings = {"hello*", "\"exact phrase\"", "[1 TO 10]", "(grouped)"})
    void isQueryExpressionShouldReturnTrueForExpressions(String query) {
        assertTrue(TurLucene.isQueryExpression(query));
    }

    @ParameterizedTest
    @ValueSource(strings = {"simple query", "hello", "test word"})
    void isQueryExpressionShouldReturnFalseForPlainQueries(String query) {
        assertFalse(TurLucene.isQueryExpression(query));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    void isQueryExpressionShouldReturnFalseForBlankOrNull(String query) {
        assertFalse(TurLucene.isQueryExpression(query));
    }
}
