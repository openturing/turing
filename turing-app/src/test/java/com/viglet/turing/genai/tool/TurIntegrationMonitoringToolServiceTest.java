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
import org.junit.jupiter.params.provider.NullAndEmptySource;
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
        instance.setVendor("AEM");
        instance.setEndpoint("http://connector:8080");
        instance.setEnabled(1);

        when(integrationRepository.findAll()).thenReturn(List.of(instance));

        String result = service.listIntegrations();
        assertThat(result)
                .contains("id;title;vendor;endpoint;enabled")
                .contains("int-1")
                .contains("AEM Connector")
                .contains("yes");
    }

    @Test
    void listIntegrationsShouldShowDisabledStatus() {
        TurIntegrationInstance instance = new TurIntegrationInstance();
        instance.setId("int-2");
        instance.setTitle("WP Connector");
        instance.setVendor("WP");
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

        JSONObject record = new JSONObject();
        record.put("objectId", "obj-1");
        record.put("name", "test-doc");
        record.put("status", "INDEXED");

        JSONObject json = new JSONObject();
        json.put("sources", new JSONArray().put("aem-source"));
        json.put("indexing", new JSONArray().put(record));

        String result = (String) method.invoke(service, json.toString());
        assertThat(result)
                .contains("aem-source")
                .contains("Indexing records: 1")
                .contains("obj-1")
                .contains("test-doc")
                .contains("INDEXED");
    }

    @Test
    void formatMonitoringResponseShouldFallbackOnBadJson() throws Exception {
        Method method = TurIntegrationMonitoringToolService.class
                .getDeclaredMethod("formatMonitoringResponse", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, "not json");
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
}
