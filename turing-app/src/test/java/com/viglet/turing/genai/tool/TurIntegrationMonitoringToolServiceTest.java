package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.persistence.model.integration.TurIntegrationInstance;
import com.viglet.turing.persistence.repository.integration.TurIntegrationInstanceRepository;

/**
 * Tests for TurIntegrationMonitoringToolService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurIntegrationMonitoringToolServiceTest {

    @Mock
    private TurIntegrationInstanceRepository integrationRepository;

    private TurIntegrationMonitoringToolService service;

    @BeforeEach
    void setUp() {
        service = new TurIntegrationMonitoringToolService(integrationRepository);
    }

    @Test
    void listIntegrationsShouldReturnNoInstancesMessage() {
        when(integrationRepository.findAll()).thenReturn(Collections.emptyList());
        String result = service.listIntegrations();
        assertThat(result).isEqualTo("No integration instances configured.");
    }

    @Test
    void listIntegrationsShouldReturnFormattedTable() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setId("int-1");
        instance.setTitle("AEM Connector");
        instance.setEndpoint("http://connector:8080");
        instance.setEnabled(1);

        when(integrationRepository.findAll()).thenReturn(List.of(instance));

        String result = service.listIntegrations();
        assertThat(result)
                .contains("id;title;endpoint;enabled")
                .contains("int-1")
                .contains("AEM Connector")
                .contains("yes");
    }

    @Test
    void listIntegrationsShouldShowDisabledStatus() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setId("int-2");
        instance.setTitle("WP Connector");
        instance.setEndpoint("http://wp:8080");
        instance.setEnabled(0);

        when(integrationRepository.findAll()).thenReturn(List.of(instance));

        String result = service.listIntegrations();
        assertThat(result).contains("no");
    }

    @Test
    void getIndexingOverviewShouldReturnNotFoundForMissingIntegration() {
        when(integrationRepository.findById("missing")).thenReturn(Optional.empty());
        String result = service.getIndexingOverview("missing");
        assertThat(result).contains("Integration not found");
    }

    @Test
    void getIndexingOverviewShouldReturnDisabledMessage() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setTitle("Test");
        instance.setEnabled(0);
        when(integrationRepository.findById("int-1")).thenReturn(Optional.of(instance));

        String result = service.getIndexingOverview("int-1");
        assertThat(result).contains("disabled");
    }

    @Test
    void sanitizePathShouldRemoveSpecialCharacters() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("sanitizePath", String.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, "normal-path_1.0")).isEqualTo("normal-path_1.0");
        assertThat(method.invoke(service, "path/../etc/passwd")).isEqualTo("path..etcpasswd");
        assertThat(method.invoke(service, "path with spaces")).isEqualTo("pathwithspaces");
        assertThat(method.invoke(service, (String) null)).isEqualTo("");
    }

    @Test
    void truncateShouldHandleNullAndShortStrings() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("truncate", String.class, int.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, null, 100)).isEqualTo("");
        assertThat(method.invoke(service, "short", 100)).isEqualTo("short");
        assertThat(method.invoke(service, "hello world", 5)).isEqualTo("hello...");
    }

    @Test
    void putIfNotBlankShouldSkipNullAndBlank() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("putIfNotBlank", JSONObject.class, String.class, String.class);
        method.setAccessible(true);

        JSONObject json = new JSONObject();
        method.invoke(service, json, "key1", null);
        method.invoke(service, json, "key2", "");
        method.invoke(service, json, "key3", "   ");
        assertThat(json.has("key1")).isFalse();
        assertThat(json.has("key2")).isFalse();
        assertThat(json.has("key3")).isFalse();
    }

    @Test
    void putIfNotBlankShouldAddValue() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("putIfNotBlank", JSONObject.class, String.class, String.class);
        method.setAccessible(true);

        JSONObject json = new JSONObject();
        method.invoke(service, json, "key", "value");
        assertThat(json.getString("key")).isEqualTo("value");
    }

    @Test
    void formatMonitoringResponseShouldHandleEmptyIndexing() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("formatMonitoringResponse", String.class);
        method.setAccessible(true);

        JSONObject json = new JSONObject();
        json.put("indexing", new JSONArray());
        String result = (String) method.invoke(service, json.toString());
        assertThat(result).contains("No indexing records found");
    }

    @Test
    void formatMonitoringResponseShouldFormatRecords() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("formatMonitoringResponse", String.class);
        method.setAccessible(true);

        JSONObject entry = new JSONObject();
        entry.put("objectId", "obj-1");
        entry.put("name", "test-doc");
        entry.put("status", "INDEXED");

        JSONObject json = new JSONObject();
        json.put("sources", new JSONArray().put("aem-source"));
        json.put("indexing", new JSONArray().put(entry));

        String result = (String) method.invoke(service, json.toString());
        assertThat(result)
                .contains("aem-source")
                .contains("Indexing records: 1")
                .contains("obj-1")
                .contains("test-doc")
                .contains("INDEXED");
    }

    @ParameterizedTest(name = "{0} falls back on bad JSON [{1}]")
    @CsvSource({
            "formatMonitoringResponse, 'not json'",
            "formatSearchResponse, 'not json'",
            "formatStatsResponse, 'not an array'",
            "formatValidationResponse, 'not json'"
    })
    void formatResponseShouldFallbackOnBadJson(String methodName, String input) throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod(methodName, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, input);
        assertThat(result).contains("Raw response");
    }

    @Test
    void formatSearchResponseShouldFormatPagination() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("formatSearchResponse", String.class);
        method.setAccessible(true);

        JSONObject json = new JSONObject();
        json.put("totalElements", 42);
        json.put("totalPages", 5);
        json.put("page", 0);
        json.put("content", new JSONArray());

        String result = (String) method.invoke(service, json.toString());
        assertThat(result)
                .contains("42 total entries")
                .contains("page 1 of 5")
                .contains("No records match");
    }

    @Test
    void formatStatsResponseShouldFormatOperations() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("formatStatsResponse", String.class);
        method.setAccessible(true);

        JSONObject stat = new JSONObject();
        stat.put("provider", "aem");
        stat.put("source", "content");
        stat.put("operationType", "FULL_INDEX");
        stat.put("documentCount", 1500);

        JSONArray stats = new JSONArray().put(stat);

        String result = (String) method.invoke(service, stats.toString());
        assertThat(result)
                .contains("Indexing Statistics: 1 operation(s)")
                .contains("Provider: aem")
                .contains("FULL_INDEX")
                .contains("1500");
    }

    @Test
    void formatStatsResponseShouldReturnEmptyMessage() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("formatStatsResponse", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, "[]");
        assertThat(result).isEqualTo("No indexing statistics available.");
    }

    @Test
    void formatValidationResponseShouldShowConsistent() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("formatValidationResponse", String.class);
        method.setAccessible(true);

        JSONObject json = new JSONObject();
        json.put("missing", new JSONObject());
        json.put("extra", new JSONObject());

        String result = (String) method.invoke(service, json.toString());
        assertThat(result)
                .contains("Missing (in DB but not indexed): 0")
                .contains("Extra (indexed but not in DB): 0")
                .contains("consistent");
    }

    @Test
    void formatValidationResponseShouldShowDiscrepancies() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("formatValidationResponse", String.class);
        method.setAccessible(true);

        JSONObject missing = new JSONObject();
        missing.put("source1", new JSONArray().put("id-1").put("id-2"));

        JSONObject extra = new JSONObject();
        extra.put("source1", new JSONArray().put("id-3"));

        JSONObject json = new JSONObject();
        json.put("missing", missing);
        json.put("extra", extra);

        String result = (String) method.invoke(service, json.toString());
        assertThat(result)
                .contains("Missing (in DB but not indexed): 2")
                .contains("Extra (indexed but not in DB): 1")
                .contains("id-1")
                .contains("id-2")
                .contains("id-3");
    }

    @Test
    void toStringListShouldConvertJsonArray() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("toStringList", JSONArray.class);
        method.setAccessible(true);

        JSONArray array = new JSONArray().put("a").put("b").put("c");
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>) method.invoke(service, array);
        assertThat(result).containsExactly("a", "b", "c");
    }

    @Test
    void countMapEntriesShouldReturnZeroForNull() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("countMapEntries", JSONObject.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, (JSONObject) null)).isEqualTo(0);
    }

    @Test
    void countMapEntriesShouldCountAllItems() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("countMapEntries", JSONObject.class);
        method.setAccessible(true);

        JSONObject map = new JSONObject();
        map.put("src1", new JSONArray().put("a").put("b"));
        map.put("src2", new JSONArray().put("c"));

        assertThat(method.invoke(service, map)).isEqualTo(3);
    }

    @Test
    void countMapEntriesShouldReturnZeroForEmptyObject() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("countMapEntries", JSONObject.class);
        method.setAccessible(true);

        assertThat(method.invoke(service, new JSONObject())).isEqualTo(0);
    }

    @Test
    void getIndexingBySourceShouldReturnNotFoundForMissingIntegration() {
        when(integrationRepository.findById("missing")).thenReturn(Optional.empty());
        String result = service.getIndexingBySource("missing", "aem");
        assertThat(result).contains("Integration not found");
    }

    @Test
    void getIndexingBySourceShouldReturnDisabledMessage() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setTitle("Test");
        instance.setEnabled(0);
        when(integrationRepository.findById("int-1")).thenReturn(Optional.of(instance));

        String result = service.getIndexingBySource("int-1", "aem");
        assertThat(result).contains("disabled");
    }

    @Test
    void searchIndexingContentShouldReturnNotFoundForMissingIntegration() {
        when(integrationRepository.findById("missing")).thenReturn(Optional.empty());
        String result = service.searchIndexingContent("missing", null, null, null, null, null, null, null, null, 10, "desc");
        assertThat(result).contains("Integration not found");
    }

    @Test
    void searchIndexingContentShouldReturnDisabledMessage() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setTitle("Test");
        instance.setEnabled(0);
        when(integrationRepository.findById("int-1")).thenReturn(Optional.of(instance));

        String result = service.searchIndexingContent("int-1", null, null, null, null, null, null, null, null, 10, "desc");
        assertThat(result).contains("disabled");
    }

    @Test
    void getIndexingStatsShouldReturnNotFoundForMissingIntegration() {
        when(integrationRepository.findById("missing")).thenReturn(Optional.empty());
        String result = service.getIndexingStats("missing", null);
        assertThat(result).contains("Integration not found");
    }

    @Test
    void getIndexingStatsShouldReturnDisabledMessage() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setTitle("Test");
        instance.setEnabled(0);
        when(integrationRepository.findById("int-1")).thenReturn(Optional.of(instance));

        String result = service.getIndexingStats("int-1", "aem");
        assertThat(result).contains("disabled");
    }

    @Test
    void validateIndexingConsistencyShouldReturnNotFoundForMissingIntegration() {
        when(integrationRepository.findById("missing")).thenReturn(Optional.empty());
        String result = service.validateIndexingConsistency("missing", "connector");
        assertThat(result).contains("Integration not found");
    }

    @Test
    void validateIndexingConsistencyShouldReturnDisabledMessage() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setTitle("Test");
        instance.setEnabled(0);
        when(integrationRepository.findById("int-1")).thenReturn(Optional.of(instance));

        String result = service.validateIndexingConsistency("int-1", "connector");
        assertThat(result).contains("disabled");
    }

    @Test
    void getUnprocessedContentShouldReturnNotFoundForMissingIntegration() {
        when(integrationRepository.findById("missing")).thenReturn(Optional.empty());
        String result = service.getUnprocessedContent("missing", "aem");
        assertThat(result).contains("Integration not found");
    }

    @Test
    void getUnprocessedContentShouldReturnDisabledMessage() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setTitle("Test");
        instance.setEnabled(0);
        when(integrationRepository.findById("int-1")).thenReturn(Optional.of(instance));

        String result = service.getUnprocessedContent("int-1", "aem");
        assertThat(result).contains("disabled");
    }

    @Test
    void getIndexingOverviewShouldReturnConnectionErrorForBadEndpoint() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setTitle("Bad Connector");
        instance.setEnabled(1);
        instance.setEndpoint("http://invalid-host-99999:9999");
        when(integrationRepository.findById("int-1")).thenReturn(Optional.of(instance));

        String result = service.getIndexingOverview("int-1");
        assertThat(result).contains("Error connecting to integration");
    }

    @Test
    void searchIndexingContentShouldReturnConnectionErrorForBadEndpoint() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setTitle("Bad Connector");
        instance.setEnabled(1);
        instance.setEndpoint("http://invalid-host-99999:9999");
        when(integrationRepository.findById("int-1")).thenReturn(Optional.of(instance));

        String result = service.searchIndexingContent("int-1", "obj-1", "aem",
                "INDEXED", "prod", "en", "site1", "2026-01-01", "2026-12-31", 0, null);
        assertThat(result).contains("Error connecting to integration");
    }

    @Test
    void searchIndexingContentShouldApplyDefaultRowsAndSort() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setTitle("Bad Connector");
        instance.setEnabled(1);
        instance.setEndpoint("http://invalid-host-99999:9999");
        when(integrationRepository.findById("int-1")).thenReturn(Optional.of(instance));

        // rows <= 0 should default to 20, sort blank defaults to "desc"
        String result = service.searchIndexingContent("int-1", null, null,
                null, null, null, null, null, null, -1, "");
        assertThat(result).contains("Error connecting to integration");
    }

    @Test
    void formatSearchResponseShouldFormatWithContent() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("formatSearchResponse", String.class);
        method.setAccessible(true);

        JSONObject entry = new JSONObject();
        entry.put("objectId", "obj-1");
        entry.put("name", "doc-1");
        entry.put("status", "INDEXED");

        JSONObject json = new JSONObject();
        json.put("totalElements", 1);
        json.put("totalPages", 1);
        json.put("page", 0);
        json.put("sources", new JSONArray().put("aem"));
        json.put("environments", new JSONArray().put("prod"));
        json.put("locales", new JSONArray().put("en_US"));
        json.put("sites", new JSONArray().put("site1"));
        json.put("content", new JSONArray().put(entry));

        String result = (String) method.invoke(service, json.toString());
        assertThat(result)
                .contains("1 total entries")
                .contains("Sources: aem")
                .contains("Environments: prod")
                .contains("Locales: en_US")
                .contains("Sites: site1")
                .contains("obj-1")
                .contains("INDEXED");
    }

    @Test
    void formatValidationResponseShouldHandleNullMissingAndExtra() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("formatValidationResponse", String.class);
        method.setAccessible(true);

        JSONObject json = new JSONObject();
        // no "missing" or "extra" keys at all
        String result = (String) method.invoke(service, json.toString());
        assertThat(result)
                .contains("Missing (in DB but not indexed): 0")
                .contains("Extra (indexed but not in DB): 0")
                .contains("consistent");
    }

    @Test
    void formatValidationResponseShouldTruncateLongLists() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("formatValidationResponse", String.class);
        method.setAccessible(true);

        JSONArray ids = new JSONArray();
        for (int i = 0; i < 25; i++) {
            ids.put("id-" + i);
        }
        JSONObject missing = new JSONObject();
        missing.put("source1", ids);

        JSONObject json = new JSONObject();
        json.put("missing", missing);
        json.put("extra", new JSONObject());

        String result = (String) method.invoke(service, json.toString());
        assertThat(result).contains("... and 5 more");
    }

    @Test
    void formatMonitoringResponseShouldLimitRecordsTo50() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("formatMonitoringResponse", String.class);
        method.setAccessible(true);

        JSONArray indexing = new JSONArray();
        for (int i = 0; i < 55; i++) {
            JSONObject entry = new JSONObject();
            entry.put("objectId", "obj-" + i);
            indexing.put(entry);
        }
        JSONObject json = new JSONObject();
        json.put("indexing", indexing);

        String result = (String) method.invoke(service, json.toString());
        assertThat(result)
                .contains("Indexing records: 55")
                .contains("... and 5 more records");
    }

    @Test
    void formatIndexingRecordShouldIncludeSites() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("formatIndexingRecord", StringBuilder.class, JSONObject.class);
        method.setAccessible(true);

        JSONObject item = new JSONObject();
        item.put("objectId", "obj-1");
        item.put("name", "test");
        item.put("status", "INDEXED");
        item.put("source", "aem");
        item.put("environment", "prod");
        item.put("locale", "en_US");
        item.put("transactionId", "tx-1");
        item.put("checksum", "abc123");
        item.put("created", "2026-01-01");
        item.put("modificationDate", "2026-03-01");
        item.put("sites", new JSONArray().put("site1").put("site2"));

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, item);

        String result = sb.toString();
        assertThat(result)
                .contains("ObjectId: obj-1")
                .contains("Name: test")
                .contains("Status: INDEXED")
                .contains("Source: aem")
                .contains("Environment: prod")
                .contains("Locale: en_US")
                .contains("TransactionId: tx-1")
                .contains("Checksum: abc123")
                .contains("Created: 2026-01-01")
                .contains("Modified: 2026-03-01")
                .contains("Sites: site1, site2");
    }

    @Test
    void appendJsonFieldShouldSkipMissingKeys() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("appendJsonField", StringBuilder.class, String.class,
                        JSONObject.class, String.class);
        method.setAccessible(true);

        JSONObject json = new JSONObject();
        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, "Label", json, "missing_key");
        assertThat(sb).isEmpty();
    }

    @Test
    void appendJsonFieldShouldSkipNullValues() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("appendJsonField", StringBuilder.class, String.class,
                        JSONObject.class, String.class);
        method.setAccessible(true);

        JSONObject json = new JSONObject();
        json.put("key", JSONObject.NULL);
        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, "Label", json, "key");
        assertThat(sb).isEmpty();
    }

    @Test
    void appendJsonFieldShouldAppendPresentValue() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("appendJsonField", StringBuilder.class, String.class,
                        JSONObject.class, String.class);
        method.setAccessible(true);

        JSONObject json = new JSONObject();
        json.put("key", "value");
        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, "Label", json, "key");
        assertThat(sb).hasToString("Label: value\n");
    }

    @Test
    void formatStatsResponseShouldFormatMultipleOperations() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("formatStatsResponse", String.class);
        method.setAccessible(true);

        JSONObject stat1 = new JSONObject();
        stat1.put("provider", "aem");
        stat1.put("source", "content");
        stat1.put("operationType", "FULL_INDEX");
        stat1.put("startTime", "2026-01-01T00:00:00");
        stat1.put("endTime", "2026-01-01T01:00:00");
        stat1.put("documentCount", 1500);
        stat1.put("documentsPerMinute", 25);

        JSONObject stat2 = new JSONObject();
        stat2.put("provider", "wp");
        stat2.put("source", "pages");
        stat2.put("operationType", "INCREMENTAL");

        JSONArray stats = new JSONArray().put(stat1).put(stat2);

        String result = (String) method.invoke(service, stats.toString());
        assertThat(result)
                .contains("2 operation(s)")
                .contains("Operation 1")
                .contains("Operation 2")
                .contains("Provider: aem")
                .contains("Provider: wp")
                .contains("Start: 2026-01-01T00:00:00")
                .contains("End: 2026-01-01T01:00:00")
                .contains("Docs/min: 25");
    }

    @Test
    void getIndexingStatsShouldAppendSourceToPath() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setTitle("Test");
        instance.setEnabled(1);
        instance.setEndpoint("http://invalid-host-99999:9999");
        when(integrationRepository.findById("int-1")).thenReturn(Optional.of(instance));

        // With source
        String result = service.getIndexingStats("int-1", "aem-source");
        assertThat(result).contains("Error connecting to integration");

        // Without source (null)
        String result2 = service.getIndexingStats("int-1", null);
        assertThat(result2).contains("Error connecting to integration");

        // Without source (blank)
        String result3 = service.getIndexingStats("int-1", "");
        assertThat(result3).contains("Error connecting to integration");
    }
}
