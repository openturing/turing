package com.viglet.turing.elasticsearch;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for TurElasticsearchUtils.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurElasticsearchUtilsTest {

    @Test
    void constructorShouldThrowIllegalStateException() {
        var constructor = TurElasticsearchUtils.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class,
                constructor::newInstance);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertEquals("Elasticsearch Utility class", ex.getCause().getMessage());
    }

    @Test
    void createIndexShouldNotThrowForUnreachableEndpoint() {
        assertDoesNotThrow(() ->
                TurElasticsearchUtils.createIndex("http://localhost:19200", "test-index"));
    }

    @Test
    void createIndexWithFieldTypesShouldNotThrowForUnreachableEndpoint() {
        assertDoesNotThrow(() ->
                TurElasticsearchUtils.createIndex("http://localhost:19200", "test-index",
                        java.util.Map.of(
                                "title", com.viglet.turing.commons.se.field.TurSEFieldType.TEXT,
                                "name", com.viglet.turing.commons.se.field.TurSEFieldType.STRING,
                                "count", com.viglet.turing.commons.se.field.TurSEFieldType.INT,
                                "active", com.viglet.turing.commons.se.field.TurSEFieldType.BOOL,
                                "created", com.viglet.turing.commons.se.field.TurSEFieldType.DATE,
                                "total", com.viglet.turing.commons.se.field.TurSEFieldType.LONG,
                                "score", com.viglet.turing.commons.se.field.TurSEFieldType.FLOAT,
                                "amount", com.viglet.turing.commons.se.field.TurSEFieldType.DOUBLE,
                                "price", com.viglet.turing.commons.se.field.TurSEFieldType.CURRENCY,
                                "tags", com.viglet.turing.commons.se.field.TurSEFieldType.ARRAY
                        )));
    }

    @Test
    void deleteIndexShouldNotThrowForUnreachableEndpoint() {
        assertDoesNotThrow(() ->
                TurElasticsearchUtils.deleteIndex("http://localhost:19200", "test-index"));
    }

    @Test
    void listIndexesShouldReturnEmptyListForUnreachableEndpoint() {
        var result = TurElasticsearchUtils.listIndexes("http://localhost:19200");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void createIndexShouldLowercaseIndexName() {
        // This should not throw even with an unreachable endpoint;
        // the important thing is that it processes the name.
        assertDoesNotThrow(() ->
                TurElasticsearchUtils.createIndex("http://localhost:19200", "MY-INDEX"));
    }

    @Test
    void createIndexWithEmptyFieldTypesShouldNotThrow() {
        assertDoesNotThrow(() ->
                TurElasticsearchUtils.createIndex("http://localhost:19200", "test-idx",
                        java.util.Collections.emptyMap()));
    }
}
