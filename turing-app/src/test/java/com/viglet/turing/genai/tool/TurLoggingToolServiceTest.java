package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for TurLoggingToolService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurLoggingToolServiceTest {

    private TurLoggingToolService service;

    @BeforeEach
    void setUp() {
        service = new TurLoggingToolService(
                false, "mongodb://localhost:27017", "turingLog",
                "server", "indexing", "aem");
    }

    @Test
    void searchServerLogsShouldReturnDisabledMessage() {
        String result = service.searchServerLogs("ERROR", null, null, null, 10, "desc");
        assertThat(result).isEqualTo("Logging is disabled. MongoDB is not configured.");
    }

    @Test
    void searchIndexingLogsShouldReturnDisabledMessage() {
        String result = service.searchIndexingLogs(null, null, null, null, null, null, 10, "desc");
        assertThat(result).isEqualTo("Logging is disabled. MongoDB is not configured.");
    }

    @Test
    void searchAemLogsShouldReturnDisabledMessage() {
        String result = service.searchAemLogs("INFO", null, null, null, 10, "desc");
        assertThat(result).isEqualTo("Logging is disabled. MongoDB is not configured.");
    }

    @Test
    void getLogStatsShouldReturnDisabledMessage() {
        String result = service.getLogStats("server", null, null);
        assertThat(result).isEqualTo("Logging is disabled. MongoDB is not configured.");
    }

    @Test
    void normalizeRowsShouldReturnDefaultForZeroOrNegative() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod("normalizeRows", int.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, 0)).isEqualTo(20);
        assertThat(method.invoke(service, -5)).isEqualTo(20);
    }

    @Test
    void normalizeRowsShouldCapAtMaxResults() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod("normalizeRows", int.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, 100)).isEqualTo(50);
        assertThat(method.invoke(service, 50)).isEqualTo(50);
    }

    @Test
    void normalizeRowsShouldReturnValueWhenInRange() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod("normalizeRows", int.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, 25)).isEqualTo(25);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    void normalizeSortShouldReturnDescForBlank(String sort) throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod("normalizeSort", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, sort)).isEqualTo("desc");
    }

    @Test
    void normalizeSortShouldReturnProvidedValueWhenNotBlank() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod("normalizeSort", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "asc")).isEqualTo("asc");
    }

    @Test
    void resolveCollectionNameShouldMapServerCorrectly() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod("resolveCollectionName", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "server")).isEqualTo("server");
        assertThat(method.invoke(service, "indexing")).isEqualTo("indexing");
        assertThat(method.invoke(service, "aem")).isEqualTo("aem");
    }

    @Test
    void resolveCollectionNameShouldReturnServerForNullOrBlank() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod("resolveCollectionName", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, (String) null)).isEqualTo("server");
        assertThat(method.invoke(service, "")).isEqualTo("server");
    }

    @Test
    void resolveCollectionNameShouldReturnNullForUnknown() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod("resolveCollectionName", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "unknown")).isNull();
    }

    @Test
    void parseDateShouldReturnNullForInvalidFormat() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod("parseDate", String.class, boolean.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "not-a-date", false)).isNull();
    }

    @Test
    void parseDateShouldParseStartOfDay() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod("parseDate", String.class, boolean.class);
        method.setAccessible(true);

        Date result = (Date) method.invoke(service, "2026-03-15", false);
        assertThat(result).isNotNull();

        LocalDate expected = LocalDate.of(2026, 3, 15);
        LocalDate actual = result.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void parseDateShouldParseEndOfDay() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod("parseDate", String.class, boolean.class);
        method.setAccessible(true);

        Date result = (Date) method.invoke(service, "2026-03-15", true);
        assertThat(result).isNotNull();

        // endOfDay should be start of next day
        LocalDate expected = LocalDate.of(2026, 3, 16);
        LocalDate actual = result.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void formatServerLogsShouldReturnNoEntriesMessage() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatServerLogs", List.class, long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, new ArrayList<Document>(), 100L);
        assertThat(result).contains("No log entries found").contains("100");
    }

    @Test
    void formatServerLogsShouldFormatEntries() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatServerLogs", List.class, long.class);
        method.setAccessible(true);

        Document doc = new Document();
        doc.put("level", "ERROR");
        doc.put("message", "Something went wrong");
        doc.put("loggerName", "com.viglet.Test");

        List<Document> docs = List.of(doc);
        String result = (String) method.invoke(service, docs, 1L);
        assertThat(result)
                .contains("1 of 1 total")
                .contains("Entry 1")
                .contains("Level: ERROR")
                .contains("Something went wrong")
                .contains("com.viglet.Test");
    }

    @Test
    void formatServerLogsShouldTruncateStackTrace() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatServerLogs", List.class, long.class);
        method.setAccessible(true);

        Document doc = new Document();
        doc.put("level", "ERROR");
        doc.put("message", "error");
        doc.put("stackTrace", "x".repeat(600));

        String result = (String) method.invoke(service, List.of(doc), 1L);
        assertThat(result).contains("truncated");
    }

    @Test
    void formatIndexingLogsShouldReturnNoEntriesMessage() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatIndexingLogs", List.class, long.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, new ArrayList<Document>(), 50L);
        assertThat(result).contains("No indexing log entries found").contains("50");
    }

    @Test
    void formatIndexingLogsShouldFormatEntries() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatIndexingLogs", List.class, long.class);
        method.setAccessible(true);

        Document doc = new Document();
        doc.put("status", "COMPLETE");
        doc.put("contentId", "doc-123");
        doc.put("resultStatus", "SUCCESS");
        doc.put("url", "http://example.com/page");

        String result = (String) method.invoke(service, List.of(doc), 1L);
        assertThat(result)
                .contains("Entry 1")
                .contains("Status: COMPLETE")
                .contains("doc-123")
                .contains("SUCCESS");
    }

    @Test
    void addLevelFilterShouldIgnoreNullAndBlank() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "addLevelFilter", List.class, String.class);
        method.setAccessible(true);

        List<Bson> filters = new ArrayList<>();
        method.invoke(service, filters, null);
        assertThat(filters).isEmpty();

        method.invoke(service, filters, "");
        assertThat(filters).isEmpty();

        method.invoke(service, filters, "  ");
        assertThat(filters).isEmpty();
    }

    @Test
    void addLevelFilterShouldAddFilterForValidLevel() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "addLevelFilter", List.class, String.class);
        method.setAccessible(true);

        List<Bson> filters = new ArrayList<>();
        method.invoke(service, filters, "error");
        assertThat(filters).hasSize(1);
    }

    @Test
    void addTextSearchFilterShouldIgnoreNullAndBlank() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "addTextSearchFilter", List.class, String.class, String[].class);
        method.setAccessible(true);

        List<Bson> filters = new ArrayList<>();
        method.invoke(service, filters, null, new String[]{"field1"});
        assertThat(filters).isEmpty();

        method.invoke(service, filters, "  ", new String[]{"field1"});
        assertThat(filters).isEmpty();
    }

    @Test
    void addTextSearchFilterShouldAddFilterForValidSearch() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "addTextSearchFilter", List.class, String.class, String[].class);
        method.setAccessible(true);

        List<Bson> filters = new ArrayList<>();
        method.invoke(service, filters, "error message", new String[]{"message", "stackTrace"});
        assertThat(filters).hasSize(1);
    }
}
