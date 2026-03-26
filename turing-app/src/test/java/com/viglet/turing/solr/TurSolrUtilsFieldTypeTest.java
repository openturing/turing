package com.viglet.turing.solr;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Tests for TurSolrUtils field type mapping and query parsing.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurSolrUtilsFieldTypeTest {

    @ParameterizedTest
    @CsvSource({
        "TEXT, text_general",
        "STRING, string",
        "INT, pint",
        "BOOL, boolean",
        "DATE, pdate",
        "LONG, plong",
        "ARRAY, strings",
        "FLOAT, pfloat",
        "DOUBLE, pdouble",
        "CURRENCY, currency"
    })
    void getSolrFieldTypeShouldMapAllTypes(String fieldType, String expected) {
        assertEquals(expected, TurSolrUtils.getSolrFieldType(TurSEFieldType.valueOf(fieldType)));
    }

    @ParameterizedTest
    @EnumSource(TurSEFieldType.class)
    void getSolrFieldTypeShouldNeverReturnNull(TurSEFieldType type) {
        assertNotNull(TurSolrUtils.getSolrFieldType(type));
    }

    @ParameterizedTest
    @EnumSource(TurSEFieldType.class)
    void getSolrFieldTypeShouldNeverReturnEmptyString(TurSEFieldType type) {
        assertFalse(TurSolrUtils.getSolrFieldType(type).isEmpty());
    }

    @Test
    void getValueFromQueryWithColonShouldReturnValue() {
        assertEquals("test", TurSolrUtils.getValueFromQuery("field:test"));
    }

    @Test
    void getValueFromQueryWithoutColonShouldReturnOriginal() {
        assertEquals("nocolon", TurSolrUtils.getValueFromQuery("nocolon"));
    }

    @Test
    void getValueFromQueryWithMultipleColonsShouldReturnEverythingAfterFirst() {
        assertEquals("value:extra", TurSolrUtils.getValueFromQuery("field:value:extra"));
    }

    @Test
    void getValueFromQueryWithQuotedValueShouldReturnValue() {
        assertEquals("\"hello world\"", TurSolrUtils.getValueFromQuery("field:\"hello world\""));
    }

    @Test
    void getValueFromQueryWithSpacesShouldPreserveSpaces() {
        assertEquals("hello world", TurSolrUtils.getValueFromQuery("field:hello world"));
    }

    @Test
    void getSolrFieldTypeForTextShouldBeTextGeneral() {
        assertEquals("text_general", TurSolrUtils.getSolrFieldType(TurSEFieldType.TEXT));
    }

    @Test
    void getSolrFieldTypeForCurrencyShouldBeCurrency() {
        assertEquals("currency", TurSolrUtils.getSolrFieldType(TurSEFieldType.CURRENCY));
    }

    @Test
    void getSolrFieldTypeForArrayShouldBeStrings() {
        assertEquals("strings", TurSolrUtils.getSolrFieldType(TurSEFieldType.ARRAY));
    }
}
