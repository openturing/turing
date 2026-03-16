package com.viglet.turing.solr;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.viglet.turing.commons.se.field.TurSEFieldType;

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
}
