package com.viglet.turing.solr;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.solr.common.SolrDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.se.result.TurSEResult;

class TurSolrUtilsTest {

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
    void getSolrFieldTypeShouldMapCorrectly(String fieldType, String expected) {
        assertEquals(expected, TurSolrUtils.getSolrFieldType(TurSEFieldType.valueOf(fieldType)));
    }

    @Test
    void getValueFromQueryShouldReturnValueAfterColon() {
        assertEquals("test", TurSolrUtils.getValueFromQuery("field:test"));
    }

    @Test
    void getValueFromQueryShouldReturnWholeStringWhenNoColon() {
        assertEquals("nocolon", TurSolrUtils.getValueFromQuery("nocolon"));
    }

    @Test
    void createTurSEResultFromDocumentShouldMapAllFields() {
        SolrDocument doc = new SolrDocument();
        doc.addField("id", "123");
        doc.addField("title", "Test Document");
        doc.addField("score", 1.5f);

        TurSEResult result = TurSolrUtils.createTurSEResultFromDocument(doc);

        assertNotNull(result);
        assertEquals("123", result.getFields().get("id"));
        assertEquals("Test Document", result.getFields().get("title"));
    }

    @Test
    void isCreateCopyFieldByCoreShouldReturnFalseForNonTextField() {
        assertFalse(TurSolrUtils.isCreateCopyFieldByCore(null, null, "field", TurSEFieldType.STRING));
    }

    @Test
    void isCreateCopyFieldByCoreShouldReturnFalseForStrSuffix() {
        assertFalse(TurSolrUtils.isCreateCopyFieldByCore(null, null, "field_str", TurSEFieldType.TEXT));
    }

    @Test
    void firstRowPositionFromCurrentPageShouldCalculateCorrectly() {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setP(2);
        searchParams.setRows(10);
        TurSEParameters params = new TurSEParameters(searchParams);

        assertEquals(10, TurSolrUtils.firstRowPositionFromCurrentPage(params));
    }

    @Test
    void lastRowPositionFromCurrentPageShouldCalculateCorrectly() {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setP(2);
        searchParams.setRows(10);
        TurSEParameters params = new TurSEParameters(searchParams);

        assertEquals(20, TurSolrUtils.lastRowPositionFromCurrentPage(params));
    }

    @Test
    void firstRowPositionFromFirstPageShouldBeZero() {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setP(1);
        searchParams.setRows(10);
        TurSEParameters params = new TurSEParameters(searchParams);

        assertEquals(0, TurSolrUtils.firstRowPositionFromCurrentPage(params));
    }
}
