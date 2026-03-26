package com.viglet.turing.solr;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.solr.common.SolrDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.se.result.TurSEResult;

/**
 * Tests for TurSolrUtils.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurSolrUtilsTest {

    // ---- Constructor ----

    @Test
    void constructorShouldThrowIllegalStateException() {
        var constructor = TurSolrUtils.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var ex = assertThrows(java.lang.reflect.InvocationTargetException.class,
                constructor::newInstance);
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertEquals("Solr Utility class", ex.getCause().getMessage());
    }

    // ---- getSolrFieldType ----

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

    @ParameterizedTest
    @EnumSource(TurSEFieldType.class)
    void getSolrFieldTypeShouldReturnNonNullForAllTypes(TurSEFieldType type) {
        assertNotNull(TurSolrUtils.getSolrFieldType(type));
        assertFalse(TurSolrUtils.getSolrFieldType(type).isEmpty());
    }

    // ---- getValueFromQuery ----

    @Test
    void getValueFromQueryShouldReturnValueAfterColon() {
        assertEquals("test", TurSolrUtils.getValueFromQuery("field:test"));
    }

    @Test
    void getValueFromQueryShouldReturnWholeStringWhenNoColon() {
        assertEquals("nocolon", TurSolrUtils.getValueFromQuery("nocolon"));
    }

    @Test
    void getValueFromQueryWithMultipleColonsShouldReturnEverythingAfterFirst() {
        assertEquals("value:extra", TurSolrUtils.getValueFromQuery("field:value:extra"));
    }

    @Test
    void getValueFromQueryWithEmptyStringShouldReturnEmpty() {
        assertEquals("", TurSolrUtils.getValueFromQuery(""));
    }

    @Test
    void getValueFromQueryWithOnlyColonShouldReturnOriginal() {
        // TurCommonsUtils.getKeyValueFromColon returns empty Optional for "field:"
        // so the fallback is the original string
        assertEquals("field:", TurSolrUtils.getValueFromQuery("field:"));
    }

    @Test
    void getValueFromQueryWithColonAtStartShouldReturnValue() {
        String result = TurSolrUtils.getValueFromQuery(":value");
        assertEquals("value", result);
    }

    // ---- createTurSEResultFromDocument ----

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
    void createTurSEResultFromEmptyDocumentShouldReturnEmptyFields() {
        SolrDocument doc = new SolrDocument();
        TurSEResult result = TurSolrUtils.createTurSEResultFromDocument(doc);
        assertNotNull(result);
        assertTrue(result.getFields().isEmpty());
    }

    @Test
    void createTurSEResultFromDocumentWithMultiValuedField() {
        SolrDocument doc = new SolrDocument();
        doc.addField("id", "1");
        doc.addField("tags", java.util.List.of("java", "spring"));

        TurSEResult result = TurSolrUtils.createTurSEResultFromDocument(doc);

        assertNotNull(result);
        Object tags = result.getFields().get("tags");
        assertInstanceOf(java.util.List.class, tags);
    }

    // ---- isCreateCopyFieldByCore ----

    @Test
    void isCreateCopyFieldByCoreShouldReturnFalseForNonTextField() {
        assertFalse(TurSolrUtils.isCreateCopyFieldByCore(null, null, "field", TurSEFieldType.STRING));
    }

    @Test
    void isCreateCopyFieldByCoreShouldReturnFalseForStrSuffix() {
        assertFalse(TurSolrUtils.isCreateCopyFieldByCore(null, null, "field_str", TurSEFieldType.TEXT));
    }

    @ParameterizedTest
    @EnumSource(value = TurSEFieldType.class, names = {"STRING", "INT", "BOOL", "DATE", "LONG", "ARRAY", "FLOAT", "DOUBLE", "CURRENCY"})
    void isCreateCopyFieldByCoreShouldReturnFalseForNonTextTypes(TurSEFieldType type) {
        assertFalse(TurSolrUtils.isCreateCopyFieldByCore(null, null, "field", type));
    }

    // ---- isDeleteCopyFieldByCore ----

    @Test
    void isDeleteCopyFieldByCoreShouldReturnFalseForNonTextField() {
        assertFalse(TurSolrUtils.isDeleteCopyFieldByCore(null, null, "field", TurSEFieldType.STRING));
    }

    @Test
    void isDeleteCopyFieldByCoreShouldReturnFalseForStrSuffix() {
        assertFalse(TurSolrUtils.isDeleteCopyFieldByCore(null, null, "field_str", TurSEFieldType.TEXT));
    }

    // ---- firstRowPositionFromCurrentPage ----

    @Test
    void firstRowPositionFromCurrentPageShouldCalculateCorrectly() {
        TurSEParameters params = createParams(2, 10);
        assertEquals(10, TurSolrUtils.firstRowPositionFromCurrentPage(params));
    }

    @Test
    void firstRowPositionFromFirstPageShouldBeZero() {
        TurSEParameters params = createParams(1, 10);
        assertEquals(0, TurSolrUtils.firstRowPositionFromCurrentPage(params));
    }

    @Test
    void firstRowPositionPage3Rows5ShouldBe10() {
        TurSEParameters params = createParams(3, 5);
        assertEquals(10, TurSolrUtils.firstRowPositionFromCurrentPage(params));
    }

    // ---- lastRowPositionFromCurrentPage ----

    @Test
    void lastRowPositionFromCurrentPageShouldCalculateCorrectly() {
        TurSEParameters params = createParams(2, 10);
        assertEquals(20, TurSolrUtils.lastRowPositionFromCurrentPage(params));
    }

    @Test
    void lastRowPositionPage1Rows10ShouldBe10() {
        TurSEParameters params = createParams(1, 10);
        assertEquals(10, TurSolrUtils.lastRowPositionFromCurrentPage(params));
    }

    @Test
    void lastRowPositionPage5Rows3ShouldBe15() {
        TurSEParameters params = createParams(5, 3);
        assertEquals(15, TurSolrUtils.lastRowPositionFromCurrentPage(params));
    }

    // ---- Constants ----

    @Test
    void strSuffixConstantShouldBeCorrect() {
        assertEquals("_str", TurSolrUtils.STR_SUFFIX);
    }

    @Test
    void schemaApiUrlConstantShouldContainPlaceholders() {
        assertTrue(TurSolrUtils.SCHEMA_API_URL.contains("%s"));
    }

    // ---- Helper ----

    private TurSEParameters createParams(int page, int rows) {
        TurSNSearchParams searchParams = new TurSNSearchParams();
        searchParams.setP(page);
        searchParams.setRows(rows);
        return new TurSEParameters(searchParams);
    }
}
