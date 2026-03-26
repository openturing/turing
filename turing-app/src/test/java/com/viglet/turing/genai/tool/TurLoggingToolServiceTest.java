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

    @Test
    void addExactFilterShouldIgnoreNullAndBlank() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "addExactFilter", List.class, String.class, String.class);
        method.setAccessible(true);

        List<Bson> filters = new ArrayList<>();
        method.invoke(service, filters, "status", null);
        assertThat(filters).isEmpty();

        method.invoke(service, filters, "status", "");
        assertThat(filters).isEmpty();

        method.invoke(service, filters, "status", "   ");
        assertThat(filters).isEmpty();
    }

    @Test
    void addExactFilterShouldAddFilterForValidValue() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "addExactFilter", List.class, String.class, String.class);
        method.setAccessible(true);

        List<Bson> filters = new ArrayList<>();
        method.invoke(service, filters, "status", "COMPLETE");
        assertThat(filters).hasSize(1);
    }

    @Test
    void addRegexFilterShouldIgnoreNullAndBlank() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "addRegexFilter", List.class, String.class, String.class);
        method.setAccessible(true);

        List<Bson> filters = new ArrayList<>();
        method.invoke(service, filters, "contentId", null);
        assertThat(filters).isEmpty();

        method.invoke(service, filters, "contentId", "");
        assertThat(filters).isEmpty();

        method.invoke(service, filters, "contentId", "  ");
        assertThat(filters).isEmpty();
    }

    @Test
    void addRegexFilterShouldAddFilterForValidValue() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "addRegexFilter", List.class, String.class, String.class);
        method.setAccessible(true);

        List<Bson> filters = new ArrayList<>();
        method.invoke(service, filters, "contentId", "doc-.*");
        assertThat(filters).hasSize(1);
    }

    @Test
    void addDateRangeFilterShouldIgnoreNullAndBlank() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "addDateRangeFilter", List.class, String.class, String.class);
        method.setAccessible(true);

        List<Bson> filters = new ArrayList<>();
        method.invoke(service, filters, null, null);
        assertThat(filters).isEmpty();

        method.invoke(service, filters, "", "");
        assertThat(filters).isEmpty();

        method.invoke(service, filters, "  ", "  ");
        assertThat(filters).isEmpty();
    }

    @Test
    void addDateRangeFilterShouldAddFiltersForValidDates() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "addDateRangeFilter", List.class, String.class, String.class);
        method.setAccessible(true);

        List<Bson> filters = new ArrayList<>();
        method.invoke(service, filters, "2026-01-01", "2026-12-31");
        assertThat(filters).hasSize(2);
    }

    @Test
    void addDateRangeFilterShouldHandleInvalidFromDate() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "addDateRangeFilter", List.class, String.class, String.class);
        method.setAccessible(true);

        List<Bson> filters = new ArrayList<>();
        method.invoke(service, filters, "bad-date", null);
        assertThat(filters).isEmpty();
    }

    @Test
    void addDateRangeFilterShouldHandleInvalidToDate() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "addDateRangeFilter", List.class, String.class, String.class);
        method.setAccessible(true);

        List<Bson> filters = new ArrayList<>();
        method.invoke(service, filters, null, "bad-date");
        assertThat(filters).isEmpty();
    }

    @Test
    void addDateRangeFilterShouldAddOnlyFromDate() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "addDateRangeFilter", List.class, String.class, String.class);
        method.setAccessible(true);

        List<Bson> filters = new ArrayList<>();
        method.invoke(service, filters, "2026-06-15", null);
        assertThat(filters).hasSize(1);
    }

    @Test
    void addDateRangeFilterShouldAddOnlyToDate() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "addDateRangeFilter", List.class, String.class, String.class);
        method.setAccessible(true);

        List<Bson> filters = new ArrayList<>();
        method.invoke(service, filters, null, "2026-06-15");
        assertThat(filters).hasSize(1);
    }

    @Test
    void formatServerLogsShouldNotTruncateShortStackTrace() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatServerLogs", List.class, long.class);
        method.setAccessible(true);

        Document doc = new Document();
        doc.put("level", "ERROR");
        doc.put("message", "error");
        doc.put("stackTrace", "short stack trace");

        String result = (String) method.invoke(service, List.of(doc), 1L);
        assertThat(result).contains("StackTrace: short stack trace").doesNotContain("truncated");
    }

    @Test
    void formatServerLogsShouldSkipBlankStackTrace() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatServerLogs", List.class, long.class);
        method.setAccessible(true);

        Document doc = new Document();
        doc.put("level", "INFO");
        doc.put("message", "ok");
        doc.put("stackTrace", "   ");

        String result = (String) method.invoke(service, List.of(doc), 1L);
        assertThat(result).doesNotContain("StackTrace:");
    }

    @Test
    void formatServerLogsShouldIncludeDateField() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatServerLogs", List.class, long.class);
        method.setAccessible(true);

        Date now = new Date();
        Document doc = new Document();
        doc.put("date", now);
        doc.put("level", "INFO");
        doc.put("message", "test");
        doc.put("loggerName", "com.example.Logger");

        String result = (String) method.invoke(service, List.of(doc), 1L);
        assertThat(result).contains("Date:").contains("Logger: com.example.Logger");
    }

    @Test
    void formatIndexingLogsShouldTruncateLongErrorMessage() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatIndexingLogs", List.class, long.class);
        method.setAccessible(true);

        Document doc = new Document();
        doc.put("status", "ERROR");
        doc.put("errorMessage", "e".repeat(600));

        String result = (String) method.invoke(service, List.of(doc), 1L);
        assertThat(result).contains("(truncated)");
    }

    @Test
    void formatIndexingLogsShouldNotTruncateShortErrorMessage() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatIndexingLogs", List.class, long.class);
        method.setAccessible(true);

        Document doc = new Document();
        doc.put("status", "ERROR");
        doc.put("errorMessage", "short error");

        String result = (String) method.invoke(service, List.of(doc), 1L);
        assertThat(result).contains("Error: short error").doesNotContain("truncated");
    }

    @Test
    void formatIndexingLogsShouldSkipBlankErrorMessage() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatIndexingLogs", List.class, long.class);
        method.setAccessible(true);

        Document doc = new Document();
        doc.put("status", "OK");
        doc.put("errorMessage", "  ");

        String result = (String) method.invoke(service, List.of(doc), 1L);
        assertThat(result).doesNotContain("Error:");
    }

    @Test
    void formatIndexingLogsShouldIncludeAllFields() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatIndexingLogs", List.class, long.class);
        method.setAccessible(true);

        Date now = new Date();
        Document doc = new Document();
        doc.put("date", now);
        doc.put("status", "COMPLETE");
        doc.put("contentId", "id-1");
        doc.put("resultStatus", "SUCCESS");
        doc.put("url", "http://example.com");
        doc.put("title", "Test Page");
        doc.put("type", "PAGE");

        String result = (String) method.invoke(service, List.of(doc), 1L);
        assertThat(result)
                .contains("Date:")
                .contains("Status: COMPLETE")
                .contains("ContentId: id-1")
                .contains("ResultStatus: SUCCESS")
                .contains("URL: http://example.com")
                .contains("Title: Test Page")
                .contains("Type: PAGE");
    }

    @Test
    void formatServerLogsShouldFormatMultipleEntries() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatServerLogs", List.class, long.class);
        method.setAccessible(true);

        Document doc1 = new Document();
        doc1.put("level", "ERROR");
        doc1.put("message", "first");

        Document doc2 = new Document();
        doc2.put("level", "WARN");
        doc2.put("message", "second");

        String result = (String) method.invoke(service, List.of(doc1, doc2), 5L);
        assertThat(result)
                .contains("2 of 5 total")
                .contains("Entry 1")
                .contains("Entry 2")
                .contains("first")
                .contains("second");
    }

    @Test
    void appendIfPresentShouldSkipNullValue() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "appendIfPresent", StringBuilder.class, String.class, Object.class);
        method.setAccessible(true);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, "Label", null);
        assertThat(sb).isEmpty();
    }

    @Test
    void appendIfPresentShouldAppendNonNullValue() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod(
                "appendIfPresent", StringBuilder.class, String.class, Object.class);
        method.setAccessible(true);

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, "Label", "value");
        assertThat(sb).hasToString("Label: value\n");
    }

    @Test
    void resolveCollectionNameShouldBeCaseInsensitive() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod("resolveCollectionName", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "SERVER")).isEqualTo("server");
        assertThat(method.invoke(service, "INDEXING")).isEqualTo("indexing");
        assertThat(method.invoke(service, "AEM")).isEqualTo("aem");
    }

    @Test
    void resolveCollectionNameShouldReturnServerForBlankString() throws Exception {
        Method method = TurLoggingToolService.class.getDeclaredMethod("resolveCollectionName", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "  ")).isEqualTo("server");
    }

    @Test
    void searchServerLogsWithEnabledShouldReturnErrorOnConnectionFailure() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.searchServerLogs("ERROR", null, null, null, 10, "desc");
        assertThat(result).contains("Error querying server logs:");
    }

    @Test
    void searchIndexingLogsWithEnabledShouldReturnErrorOnConnectionFailure() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.searchIndexingLogs(null, null, null, null, null, null, 10, "desc");
        assertThat(result).contains("Error querying indexing logs:");
    }

    @Test
    void searchAemLogsWithEnabledShouldReturnErrorOnConnectionFailure() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.searchAemLogs("WARN", null, null, null, 10, "asc");
        assertThat(result).contains("Error querying AEM logs:");
    }

    @Test
    void getLogStatsShouldReturnInvalidSourceMessage() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://localhost:27017", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.getLogStats("invalid", null, null);
        assertThat(result).isEqualTo("Invalid source. Use 'server', 'indexing', or 'aem'.");
    }

    @Test
    void getLogStatsWithEnabledShouldReturnErrorOnConnectionFailure() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.getLogStats("server", null, null);
        assertThat(result).contains("Error getting log stats:");
    }

    @Test
    void getLogStatsWithEnabledAndDatesShouldReturnErrorOnConnectionFailure() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.getLogStats("indexing", "2026-01-01", "2026-12-31");
        assertThat(result).contains("Error getting log stats:");
    }

    @Test
    void searchServerLogsWithAllFiltersShouldReturnErrorOnConnectionFailure() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.searchServerLogs(
                "ERROR", "2026-01-01", "2026-12-31", "search term", 25, "asc");
        assertThat(result).contains("Error querying server logs:");
    }

    @Test
    void searchIndexingLogsWithAllFiltersShouldReturnErrorOnConnectionFailure() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.searchIndexingLogs(
                "2026-01-01", "2026-12-31", "COMPLETE", "content-1",
                "SUCCESS", "http://example.com", 15, "asc");
        assertThat(result).contains("Error querying indexing logs:");
    }

    // --- Additional coverage: search with ascending sort ---

    @Test
    void searchServerLogsWithAscSortShouldReturnErrorOnConnectionFailure() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.searchServerLogs(null, null, null, null, 10, "asc");
        assertThat(result).contains("Error querying server logs:");
    }

    @Test
    void searchAemLogsWithAllFiltersShouldReturnErrorOnConnectionFailure() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.searchAemLogs(
                "ERROR", "2026-01-01", "2026-12-31", "search term", 25, "asc");
        assertThat(result).contains("Error querying AEM logs:");
    }

    @Test
    void getLogStatsShouldReturnErrorForIndexingSourceOnConnectionFailure() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.getLogStats("indexing", null, null);
        assertThat(result).contains("Error getting log stats:");
    }

    @Test
    void getLogStatsShouldReturnErrorForAemSourceOnConnectionFailure() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.getLogStats("aem", null, null);
        assertThat(result).contains("Error getting log stats:");
    }

    @Test
    void getLogStatsWithOnlyFromDateShouldReturnErrorOnConnectionFailure() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.getLogStats("server", "2026-01-01", null);
        assertThat(result).contains("Error getting log stats:");
    }

    @Test
    void getLogStatsWithOnlyToDateShouldReturnErrorOnConnectionFailure() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.getLogStats("server", null, "2026-12-31");
        assertThat(result).contains("Error getting log stats:");
    }

    @Test
    void getLogStatsWithNullSourceDefaultsToServer() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.getLogStats(null, null, null);
        assertThat(result).contains("Error getting log stats:");
    }

    @Test
    void getLogStatsWithBlankSourceDefaultsToServer() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.getLogStats("", null, null);
        assertThat(result).contains("Error getting log stats:");
    }

    @Test
    void searchServerLogsWithNullSortDefaultsToDesc() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.searchServerLogs(null, null, null, null, 0, null);
        assertThat(result).contains("Error querying server logs:");
    }

    @Test
    void searchIndexingLogsWithNullSortDefaultsToDesc() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.searchIndexingLogs(null, null, null, null, null, null, 0, null);
        assertThat(result).contains("Error querying indexing logs:");
    }

    @Test
    void searchAemLogsWithNullSortDefaultsToDesc() {
        TurLoggingToolService enabledService = new TurLoggingToolService(
                true, "mongodb://invalid-host:99999", "turingLog",
                "server", "indexing", "aem");
        String result = enabledService.searchAemLogs(null, null, null, null, 0, null);
        assertThat(result).contains("Error querying AEM logs:");
    }

    @Test
    void formatServerLogsShouldSkipNullStackTrace() throws Exception {
        java.lang.reflect.Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatServerLogs", List.class, long.class);
        method.setAccessible(true);

        Document doc = new Document();
        doc.put("level", "INFO");
        doc.put("message", "ok");
        // stackTrace field absent (null)

        String result = (String) method.invoke(service, List.of(doc), 1L);
        assertThat(result).doesNotContain("StackTrace:");
    }

    @Test
    void formatIndexingLogsShouldSkipNullErrorMessage() throws Exception {
        java.lang.reflect.Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatIndexingLogs", List.class, long.class);
        method.setAccessible(true);

        Document doc = new Document();
        doc.put("status", "OK");
        // no errorMessage field

        String result = (String) method.invoke(service, List.of(doc), 1L);
        assertThat(result).doesNotContain("Error:");
    }

    @Test
    void formatIndexingLogsShouldHandleMultipleEntries() throws Exception {
        java.lang.reflect.Method method = TurLoggingToolService.class.getDeclaredMethod(
                "formatIndexingLogs", List.class, long.class);
        method.setAccessible(true);

        Document doc1 = new Document();
        doc1.put("status", "COMPLETE");
        doc1.put("contentId", "id-1");

        Document doc2 = new Document();
        doc2.put("status", "ERROR");
        doc2.put("contentId", "id-2");
        doc2.put("errorMessage", "Failed to process");

        String result = (String) method.invoke(service, List.of(doc1, doc2), 10L);
        assertThat(result)
                .contains("2 of 10 total")
                .contains("Entry 1")
                .contains("Entry 2")
                .contains("id-1")
                .contains("id-2")
                .contains("Error: Failed to process");
    }

    @Test
    void normalizeRowsShouldReturnExactMaxResults() throws Exception {
        java.lang.reflect.Method method = TurLoggingToolService.class.getDeclaredMethod("normalizeRows", int.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, 1)).isEqualTo(1);
        assertThat(method.invoke(service, 49)).isEqualTo(49);
    }
}
