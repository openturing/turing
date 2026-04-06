package com.viglet.turing.genai.tool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.integration.TurIntegrationInstance;
import com.viglet.turing.persistence.repository.integration.TurIntegrationInstanceRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Tool calling service for integration monitoring and indexing analysis via LLM.
 * Provides tools to query connector indexing status, statistics, and content tracking,
 * enabling the LLM to cross-reference with logging tools for root cause analysis.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
@Slf4j
@Service
public class TurIntegrationMonitoringToolService {

    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String SOURCE_FIELD = "source";
    private static final String RECORD_SEPARATOR = " ---\n";
    private static final String RAW_RESPONSE_PREFIX = "Raw response: ";

    private final TurIntegrationInstanceRepository integrationRepository;
    private final HttpClient httpClient;

    public TurIntegrationMonitoringToolService(TurIntegrationInstanceRepository integrationRepository) {
        this.integrationRepository = integrationRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Tool(name = "list_integrations", description = ".")
    public String listIntegrations() {
        log.info("[Integration Tool] list_integrations called");
        List<TurIntegrationInstance> instances = integrationRepository.findAll();
        if (instances.isEmpty()) {
            return "No integration instances configured.";
        }
        StringBuilder sb = new StringBuilder("id;title;endpoint;enabled\n");
        for (TurIntegrationInstance instance : instances) {
            sb.append(instance.getId()).append(";")
                    .append(instance.getTitle()).append(";")
                    .append(instance.getEndpoint()).append(";")
                    .append(instance.getEnabled() == 1 ? "yes" : "no").append("\n");
        }
        return sb.toString();
    }

    @Tool(name = "get_indexing_overview", description = ".")
    public String getIndexingOverview(String integrationId) {
        log.info("[Integration Tool] get_indexing_overview called: integrationId={}", integrationId);
        return callConnectorEndpoint(integrationId,
                "/api/v2/connector/monitoring/indexing",
                this::formatMonitoringResponse);
    }

    @Tool(name = "get_indexing_by_source", description = ".")
    public String getIndexingBySource(String integrationId, String source) {
        log.info("[Integration Tool] get_indexing_by_source called: integrationId={}, source={}", integrationId, source);
        return callConnectorEndpoint(integrationId,
                "/api/v2/connector/monitoring/indexing/" + sanitizePath(source),
                this::formatMonitoringResponse);
    }

    @Tool(name = "search_indexing_content", description = ".")
    public String searchIndexingContent(String integrationId, String objectId, String source,
                                        String status, String environment, String locale,
                                        String site, String dateFrom, String dateTo,
                                        int rows, String sortDirection) {
        log.info("[Integration Tool] search_indexing_content called: integrationId={}, objectId={}, source={}, status={}, rows={}",
                integrationId, objectId, source, status, rows);
        int effectiveRows = (rows <= 0) ? 20 : Math.min(rows, 50);
        String effectiveSort = (sortDirection == null || sortDirection.isBlank()) ? "desc" : sortDirection;

        JSONObject requestBody = new JSONObject();
        requestBody.put("page", 0);
        requestBody.put("size", effectiveRows);
        requestBody.put("sortBy", "modificationDate");
        requestBody.put("sortDirection", effectiveSort);
        putIfNotBlank(requestBody, "objectId", objectId);
        putIfNotBlank(requestBody, SOURCE_FIELD, source);
        putIfNotBlank(requestBody, "environment", environment);
        putIfNotBlank(requestBody, "locale", locale);
        putIfNotBlank(requestBody, "site", site);
        putIfNotBlank(requestBody, "dateFrom", dateFrom);
        putIfNotBlank(requestBody, "dateTo", dateTo);
        if (status != null && !status.isBlank()) {
            requestBody.put("statuses", new JSONArray().put(status));
        }

        return callConnectorPostEndpoint(integrationId,
                "/api/v2/connector/monitoring/indexing/_search",
                requestBody.toString(),
                this::formatSearchResponse);
    }

    @Tool(name = "get_indexing_stats", description = ".")
    public String getIndexingStats(String integrationId, String source) {
        log.info("[Integration Tool] get_indexing_stats called: integrationId={}, source={}", integrationId, source);
        String path = "/api/v2/connector/monitoring/indexing/stats";
        if (source != null && !source.isBlank()) {
            path += "/" + sanitizePath(source);
        }
        return callConnectorEndpoint(integrationId, path, this::formatStatsResponse);
    }

    @Tool(name = "validate_indexing_consistency", description = ".")
    public String validateIndexingConsistency(String integrationId, String connectorName) {
        log.info("[Integration Tool] validate_indexing_consistency called: integrationId={}, connectorName={}",
                integrationId, connectorName);
        return callConnectorEndpoint(integrationId,
                "/api/v2/connector/validate/" + sanitizePath(connectorName),
                this::formatValidationResponse);
    }

    @Tool(name = "get_unprocessed_content", description = ".")
    public String getUnprocessedContent(String integrationId, String source) {
        log.info("[Integration Tool] get_unprocessed_content called: integrationId={}, source={}", integrationId, source);
        return callConnectorEndpoint(integrationId,
                "/api/v2/connector/monitoring/indexing/unprocessed/" + sanitizePath(source),
                this::formatMonitoringResponse);
    }

    private String callConnectorEndpoint(String integrationId, String path,
                                         java.util.function.UnaryOperator<String> formatter) {
        var optInstance = integrationRepository.findById(integrationId);
        if (optInstance.isEmpty()) {
            return "Integration not found: " + integrationId + ". Use list_integrations to see available ones.";
        }
        TurIntegrationInstance instance = optInstance.get();
        if (instance.getEnabled() != 1) {
            return "Integration '" + instance.getTitle() + "' is disabled.";
        }
        String url = instance.getEndpoint() + path;
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET();
            addApiKeyHeader(instance, requestBuilder);
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return "Connector returned HTTP " + response.statusCode() + " for " + path;
            }
            return formatter.apply(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Integration Tool] Request interrupted: {}", e.getMessage(), e);
            return "Request interrupted: " + e.getMessage();
        } catch (Exception e) {
            log.error("[Integration Tool] Error calling connector: {}", e.getMessage(), e);
            return "Error connecting to integration '" + instance.getTitle() + "': " + e.getMessage();
        }
    }

    private String callConnectorPostEndpoint(String integrationId, String path,
                                              String body,
                                              java.util.function.UnaryOperator<String> formatter) {
        var optInstance = integrationRepository.findById(integrationId);
        if (optInstance.isEmpty()) {
            return "Integration not found: " + integrationId + ". Use list_integrations to see available ones.";
        }
        TurIntegrationInstance instance = optInstance.get();
        if (instance.getEnabled() != 1) {
            return "Integration '" + instance.getTitle() + "' is disabled.";
        }
        String url = instance.getEndpoint() + path;
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(TIMEOUT)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            addApiKeyHeader(instance, requestBuilder);
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                return "Connector returned HTTP " + response.statusCode() + " for " + path;
            }
            return formatter.apply(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Integration Tool] Request interrupted: {}", e.getMessage(), e);
            return "Request interrupted: " + e.getMessage();
        } catch (Exception e) {
            log.error("[Integration Tool] Error calling connector: {}", e.getMessage(), e);
            return "Error connecting to integration '" + instance.getTitle() + "': " + e.getMessage();
        }
    }

    private String formatMonitoringResponse(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            StringBuilder sb = new StringBuilder();

            JSONArray sources = json.optJSONArray("sources");
            if (sources != null && !sources.isEmpty()) {
                sb.append("Available sources: ").append(String.join(", ", toStringList(sources))).append("\n\n");
            }

            JSONArray indexing = json.optJSONArray("indexing");
            if (indexing == null || indexing.isEmpty()) {
                sb.append("No indexing records found.\n");
                return sb.toString();
            }

            sb.append("Indexing records: ").append(indexing.length()).append("\n\n");
            int limit = Math.min(indexing.length(), 50);
            for (int i = 0; i < limit; i++) {
                JSONObject item = indexing.getJSONObject(i);
                sb.append("--- Record ").append(i + 1).append(RECORD_SEPARATOR);
                formatIndexingRecord(sb, item);
                sb.append("\n");
            }
            if (indexing.length() > limit) {
                sb.append("... and ").append(indexing.length() - limit).append(" more records\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return RAW_RESPONSE_PREFIX + truncate(responseBody, 2000);
        }
    }

    private String formatSearchResponse(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            StringBuilder sb = new StringBuilder();

            long totalElements = json.optLong("totalElements", 0);
            int totalPages = json.optInt("totalPages", 0);
            int page = json.optInt("page", 0);

            sb.append("Search Results: ").append(totalElements).append(" total entries");
            sb.append(" (page ").append(page + 1).append(" of ").append(totalPages).append(")\n");

            JSONArray sources = json.optJSONArray("sources");
            if (sources != null && !sources.isEmpty()) {
                sb.append("Sources: ").append(String.join(", ", toStringList(sources))).append("\n");
            }
            JSONArray environments = json.optJSONArray("environments");
            if (environments != null && !environments.isEmpty()) {
                sb.append("Environments: ").append(String.join(", ", toStringList(environments))).append("\n");
            }
            JSONArray locales = json.optJSONArray("locales");
            if (locales != null && !locales.isEmpty()) {
                sb.append("Locales: ").append(String.join(", ", toStringList(locales))).append("\n");
            }
            JSONArray sites = json.optJSONArray("sites");
            if (sites != null && !sites.isEmpty()) {
                sb.append("Sites: ").append(String.join(", ", toStringList(sites))).append("\n");
            }

            sb.append("\n");
            JSONArray content = json.optJSONArray("content");
            if (content == null || content.isEmpty()) {
                sb.append("No records match the criteria.\n");
                return sb.toString();
            }

            for (int i = 0; i < content.length(); i++) {
                JSONObject item = content.getJSONObject(i);
                sb.append("--- Record ").append(i + 1).append(RECORD_SEPARATOR);
                formatIndexingRecord(sb, item);
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return RAW_RESPONSE_PREFIX + truncate(responseBody, 2000);
        }
    }

    private String formatStatsResponse(String responseBody) {
        try {
            JSONArray stats = new JSONArray(responseBody);
            if (stats.isEmpty()) {
                return "No indexing statistics available.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Indexing Statistics: ").append(stats.length()).append(" operation(s)\n\n");
            for (int i = 0; i < stats.length(); i++) {
                JSONObject stat = stats.getJSONObject(i);
                sb.append("--- Operation ").append(i + 1).append(RECORD_SEPARATOR);
                appendJsonField(sb, "Provider", stat, "provider");
                appendJsonField(sb, "Source", stat, SOURCE_FIELD);
                appendJsonField(sb, "Operation", stat, "operationType");
                appendJsonField(sb, "Start", stat, "startTime");
                appendJsonField(sb, "End", stat, "endTime");
                appendJsonField(sb, "Documents", stat, "documentCount");
                appendJsonField(sb, "Docs/min", stat, "documentsPerMinute");
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            return RAW_RESPONSE_PREFIX + truncate(responseBody, 2000);
        }
    }

    private String formatValidationResponse(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            StringBuilder sb = new StringBuilder();

            JSONObject missing = json.optJSONObject("missing");
            JSONObject extra = json.optJSONObject("extra");

            int missingCount = countMapEntries(missing);
            int extraCount = countMapEntries(extra);

            sb.append("Validation Results\n");
            sb.append("Missing (in DB but not indexed): ").append(missingCount).append(" item(s)\n");
            sb.append("Extra (indexed but not in DB): ").append(extraCount).append(" item(s)\n\n");

            if (missingCount == 0 && extraCount == 0) {
                sb.append("Index is consistent with the database. No discrepancies found.\n");
                return sb.toString();
            }

            appendValidationSection(sb, "--- Missing Items ---\n", missing);
            appendValidationSection(sb, "\n--- Extra Items ---\n", extra);

            return sb.toString();
        } catch (Exception e) {
            return RAW_RESPONSE_PREFIX + truncate(responseBody, 2000);
        }
    }

    private void appendValidationSection(StringBuilder sb, String header, JSONObject entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        sb.append(header);
        for (String key : entries.keySet()) {
            JSONArray ids = entries.getJSONArray(key);
            sb.append("  ").append(key).append(": ");
            int limit = Math.min(ids.length(), 20);
            for (int i = 0; i < limit; i++) {
                if (i > 0) sb.append(", ");
                sb.append(ids.getString(i));
            }
            if (ids.length() > limit) {
                sb.append(" ... and ").append(ids.length() - limit).append(" more");
            }
            sb.append("\n");
        }
    }

    private void formatIndexingRecord(StringBuilder sb, JSONObject item) {
        appendJsonField(sb, "ObjectId", item, "objectId");
        appendJsonField(sb, "Name", item, "name");
        appendJsonField(sb, "Status", item, "status");
        appendJsonField(sb, "Source", item, SOURCE_FIELD);
        appendJsonField(sb, "Environment", item, "environment");
        appendJsonField(sb, "Locale", item, "locale");
        appendJsonField(sb, "TransactionId", item, "transactionId");
        appendJsonField(sb, "Checksum", item, "checksum");
        appendJsonField(sb, "Created", item, "created");
        appendJsonField(sb, "Modified", item, "modificationDate");
        JSONArray sites = item.optJSONArray("sites");
        if (sites != null && !sites.isEmpty()) {
            sb.append("Sites: ").append(String.join(", ", toStringList(sites))).append("\n");
        }
    }

    private void appendJsonField(StringBuilder sb, String label, JSONObject json, String key) {
        if (json.has(key) && !json.isNull(key)) {
            sb.append(label).append(": ").append(json.get(key)).append("\n");
        }
    }

    private int countMapEntries(JSONObject map) {
        if (map == null || map.isEmpty()) return 0;
        int count = 0;
        for (String key : map.keySet()) {
            count += map.getJSONArray(key).length();
        }
        return count;
    }

    private List<String> toStringList(JSONArray array) {
        List<String> list = new java.util.ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            list.add(array.getString(i));
        }
        return list;
    }

    private String sanitizePath(String segment) {
        if (segment == null) return "";
        return segment.replaceAll("[^\\w\\-.]", "");
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private void putIfNotBlank(JSONObject json, String key, String value) {
        if (value != null && !value.isBlank()) {
            json.put(key, value);
        }
    }

    private void addApiKeyHeader(TurIntegrationInstance instance, HttpRequest.Builder requestBuilder) {
        if (instance.getApiToken() != null && instance.getApiToken().getToken() != null) {
            requestBuilder.header("Key", instance.getApiToken().getToken());
        }
    }
}
