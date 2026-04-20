package com.viglet.turing.genai.tool;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import lombok.extern.slf4j.Slf4j;

/**
 * Tool calling service for log analysis via LLM.
 * Provides tools to query server, indexing, and AEM logs stored in MongoDB.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.17
 */
@Slf4j
@Service
public class TurLoggingToolService {

    private static final String DATE_FIELD = "date";
    private static final String STACK_TRACE_FIELD = "stackTrace";
    private static final String MESSAGE_FIELD = "message";
    private static final String RESULT_STATUS_FIELD = "resultStatus";
    private static final String LEVEL_FIELD = "level";
    private static final String LOGGING_DISABLED_MSG = "Logging is disabled. MongoDB is not configured.";
    private static final int MAX_RESULTS = 50;

    private final boolean enabled;
    private final String connectionString;
    private final String databaseName;
    private final String serverCollectionName;
    private final String indexingCollectionName;
    private final String aemCollectionName;

    public TurLoggingToolService(
            @Value("${turing.mongodb.enabled:false}") boolean enabled,
            @Value("${turing.mongodb.uri:'mongodb://localhost:27017'}") String connectionString,
            @Value("${turing.logging.database:'turingLog'}") String databaseName,
            @Value("${turing.logging.collection.server:'server'}") String serverCollectionName,
            @Value("${turing.logging.collection.indexing:'indexing'}") String indexingCollectionName,
            @Value("${turing.logging.collection.aem:'aem'}") String aemCollectionName) {
        this.enabled = enabled;
        this.connectionString = connectionString;
        this.databaseName = databaseName;
        this.serverCollectionName = serverCollectionName;
        this.indexingCollectionName = indexingCollectionName;
        this.aemCollectionName = aemCollectionName;
    }

    @Tool(name = "search_server_logs", description = ".")
    public String searchServerLogs(String level, String dateFrom, String dateTo,
                                   String search, int rows, String sort) {
        log.info("[Logging Tool] search_server_logs called: level={}, dateFrom={}, dateTo={}, search={}, rows={}, sort={}",
                level, dateFrom, dateTo, search, rows, sort);
        if (!enabled) {
            return LOGGING_DISABLED_MSG;
        }
        int effectiveRows = normalizeRows(rows);
        String effectiveSort = normalizeSort(sort);

        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            MongoCollection<Document> collection = database.getCollection(serverCollectionName);

            List<Bson> filters = new ArrayList<>();
            addLevelFilter(filters, level);
            addDateRangeFilter(filters, dateFrom, dateTo);
            addTextSearchFilter(filters, search, MESSAGE_FIELD, STACK_TRACE_FIELD);

            Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);
            long totalElements = collection.countDocuments(filter);
            Bson sorting = "desc".equalsIgnoreCase(effectiveSort)
                    ? Sorts.descending(DATE_FIELD) : Sorts.ascending(DATE_FIELD);

            List<Document> docs = new ArrayList<>();
            collection.find(filter).sort(sorting).limit(effectiveRows).forEach(docs::add);

            return formatServerLogs(docs, totalElements);
        } catch (Exception e) {
            log.error("[Logging Tool] search_server_logs failed: {}", e.getMessage(), e);
            return "Error querying server logs: " + e.getMessage();
        }
    }

    @Tool(name = "search_indexing_logs", description = ".")
    public String searchIndexingLogs(String dateFrom, String dateTo, String status,
                                     String contentId, String resultStatus, String url,
                                     int rows, String sort) {
        log.info("[Logging Tool] search_indexing_logs called: dateFrom={}, dateTo={}, status={}, contentId={}, resultStatus={}, url={}, rows={}, sort={}",
                dateFrom, dateTo, status, contentId, resultStatus, url, rows, sort);
        if (!enabled) {
            return LOGGING_DISABLED_MSG;
        }
        int effectiveRows = normalizeRows(rows);
        String effectiveSort = normalizeSort(sort);

        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            MongoCollection<Document> collection = database.getCollection(indexingCollectionName);

            List<Bson> filters = new ArrayList<>();
            addDateRangeFilter(filters, dateFrom, dateTo);
            addExactFilter(filters, "status", status);
            addRegexFilter(filters, "contentId", contentId);
            addExactFilter(filters, RESULT_STATUS_FIELD, resultStatus);
            addRegexFilter(filters, "url", url);

            Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);
            long totalElements = collection.countDocuments(filter);
            Bson sorting = "desc".equalsIgnoreCase(effectiveSort)
                    ? Sorts.descending(DATE_FIELD) : Sorts.ascending(DATE_FIELD);

            List<Document> docs = new ArrayList<>();
            collection.find(filter).sort(sorting).limit(effectiveRows).forEach(docs::add);

            return formatIndexingLogs(docs, totalElements);
        } catch (Exception e) {
            log.error("[Logging Tool] search_indexing_logs failed: {}", e.getMessage(), e);
            return "Error querying indexing logs: " + e.getMessage();
        }
    }

    @Tool(name = "search_aem_logs", description = ".")
    public String searchAemLogs(String level, String dateFrom, String dateTo,
                                String search, int rows, String sort) {
        log.info("[Logging Tool] search_aem_logs called: level={}, dateFrom={}, dateTo={}, search={}, rows={}, sort={}",
                level, dateFrom, dateTo, search, rows, sort);
        if (!enabled) {
            return LOGGING_DISABLED_MSG;
        }
        int effectiveRows = normalizeRows(rows);
        String effectiveSort = normalizeSort(sort);

        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            MongoCollection<Document> collection = database.getCollection(aemCollectionName);

            List<Bson> filters = new ArrayList<>();
            addLevelFilter(filters, level);
            addDateRangeFilter(filters, dateFrom, dateTo);
            addTextSearchFilter(filters, search, MESSAGE_FIELD, STACK_TRACE_FIELD);

            Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);
            long totalElements = collection.countDocuments(filter);
            Bson sorting = "desc".equalsIgnoreCase(effectiveSort)
                    ? Sorts.descending(DATE_FIELD) : Sorts.ascending(DATE_FIELD);

            List<Document> docs = new ArrayList<>();
            collection.find(filter).sort(sorting).limit(effectiveRows).forEach(docs::add);

            return formatServerLogs(docs, totalElements);
        } catch (Exception e) {
            log.error("[Logging Tool] search_aem_logs failed: {}", e.getMessage(), e);
            return "Error querying AEM logs: " + e.getMessage();
        }
    }

    @Tool(name = "get_log_stats", description = ".")
    public String getLogStats(String source, String dateFrom, String dateTo) {
        log.info("[Logging Tool] get_log_stats called: source={}, dateFrom={}, dateTo={}", source, dateFrom, dateTo);
        if (!enabled) {
            return LOGGING_DISABLED_MSG;
        }

        String collectionName = resolveCollectionName(source);
        if (collectionName == null) {
            return "Invalid source. Use 'server', 'indexing', or 'aem'.";
        }

        try (MongoClient mongoClient = MongoClients.create(connectionString)) {
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            MongoCollection<Document> collection = database.getCollection(collectionName);

            List<Bson> dateFilters = new ArrayList<>();
            addDateRangeFilter(dateFilters, dateFrom, dateTo);
            Bson dateFilter = dateFilters.isEmpty() ? new Document() : Filters.and(dateFilters);

            long totalCount = collection.countDocuments(dateFilter);

            StringBuilder sb = new StringBuilder();
            sb.append("Log Statistics for: ").append(source.toUpperCase()).append("\n");
            if (dateFrom != null || dateTo != null) {
                sb.append("Period: ");
                if (dateFrom != null) sb.append("from ").append(dateFrom).append(" ");
                if (dateTo != null) sb.append("to ").append(dateTo);
                sb.append("\n");
            }
            sb.append("Total entries: ").append(totalCount).append("\n");

            if ("indexing".equalsIgnoreCase(source)) {
                appendIndexingStats(sb, collection, dateFilter);
            } else {
                appendLevelStats(sb, collection, dateFilter);
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("[Logging Tool] get_log_stats failed: {}", e.getMessage(), e);
            return "Error getting log stats: " + e.getMessage();
        }
    }

    private String resolveCollectionName(String source) {
        if (source == null || source.isBlank()) return serverCollectionName;
        return switch (source.toLowerCase()) {
            case "server" -> serverCollectionName;
            case "indexing" -> indexingCollectionName;
            case "aem" -> aemCollectionName;
            default -> null;
        };
    }

    private void appendLevelStats(StringBuilder sb, MongoCollection<Document> collection, Bson dateFilter) {
        sb.append("\nBreakdown by level:\n");
        for (String level : List.of("ERROR", "WARN", "INFO", "DEBUG", "TRACE")) {
            List<Bson> filters = new ArrayList<>();
            if (!(dateFilter instanceof Document d && d.isEmpty())) {
                filters.add(dateFilter);
            }
            filters.add(Filters.eq(LEVEL_FIELD, level));
            long count = collection.countDocuments(filters.isEmpty() ? new Document() : Filters.and(filters));
            if (count > 0) {
                sb.append("  ").append(level).append(": ").append(count).append("\n");
            }
        }
    }

    private void appendIndexingStats(StringBuilder sb, MongoCollection<Document> collection, Bson dateFilter) {
        sb.append("\nBreakdown by resultStatus:\n");
        for (String status : List.of("SUCCESS", "ERROR", "SKIPPED", "WARNING")) {
            List<Bson> filters = new ArrayList<>();
            if (!(dateFilter instanceof Document d && d.isEmpty())) {
                filters.add(dateFilter);
            }
            filters.add(Filters.eq(RESULT_STATUS_FIELD, status));
            long count = collection.countDocuments(filters.isEmpty() ? new Document() : Filters.and(filters));
            if (count > 0) {
                sb.append("  ").append(status).append(": ").append(count).append("\n");
            }
        }
    }

    private String formatServerLogs(List<Document> docs, long totalElements) {
        if (docs.isEmpty()) {
            return "No log entries found matching the criteria. Total in collection: " + totalElements;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(docs.size()).append(" of ").append(totalElements).append(" total entries\n\n");
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            sb.append("--- Entry ").append(i + 1).append(" ---\n");
            appendIfPresent(sb, "Date", doc.get(DATE_FIELD));
            appendIfPresent(sb, "Level", doc.getString(LEVEL_FIELD));
            appendIfPresent(sb, "Logger", doc.getString("loggerName"));
            appendIfPresent(sb, "Message", doc.getString(MESSAGE_FIELD));
            String stackTrace = doc.getString(STACK_TRACE_FIELD);
            if (stackTrace != null && !stackTrace.isBlank()) {
                String truncated = stackTrace.length() > 500
                        ? stackTrace.substring(0, 500) + "... (truncated)"
                        : stackTrace;
                sb.append("StackTrace: ").append(truncated).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String formatIndexingLogs(List<Document> docs, long totalElements) {
        if (docs.isEmpty()) {
            return "No indexing log entries found matching the criteria. Total in collection: " + totalElements;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Found ").append(docs.size()).append(" of ").append(totalElements).append(" total entries\n\n");
        for (int i = 0; i < docs.size(); i++) {
            Document doc = docs.get(i);
            sb.append("--- Entry ").append(i + 1).append(" ---\n");
            appendIfPresent(sb, "Date", doc.get(DATE_FIELD));
            appendIfPresent(sb, "Status", doc.getString("status"));
            appendIfPresent(sb, "ContentId", doc.getString("contentId"));
            appendIfPresent(sb, "ResultStatus", doc.getString(RESULT_STATUS_FIELD));
            appendIfPresent(sb, "URL", doc.getString("url"));
            appendIfPresent(sb, "Title", doc.getString("title"));
            appendIfPresent(sb, "Type", doc.getString("type"));
            String errorMessage = doc.getString("errorMessage");
            if (errorMessage != null && !errorMessage.isBlank()) {
                String truncated = errorMessage.length() > 500
                        ? errorMessage.substring(0, 500) + "... (truncated)"
                        : errorMessage;
                sb.append("Error: ").append(truncated).append("\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private void appendIfPresent(StringBuilder sb, String label, Object value) {
        if (value != null) {
            sb.append(label).append(": ").append(value).append("\n");
        }
    }

    private int normalizeRows(int rows) {
        if (rows <= 0) return 20;
        return Math.min(rows, MAX_RESULTS);
    }

    private String normalizeSort(String sort) {
        if (sort == null || sort.isBlank()) return "desc";
        return sort;
    }

    private void addLevelFilter(List<Bson> filters, String level) {
        if (level != null && !level.isBlank()) {
            filters.add(Filters.eq(LEVEL_FIELD, level.toUpperCase()));
        }
    }

    private void addDateRangeFilter(List<Bson> filters, String dateFrom, String dateTo) {
        if (dateFrom != null && !dateFrom.isBlank()) {
            Date from = parseDate(dateFrom, false);
            if (from != null) {
                filters.add(Filters.gte(DATE_FIELD, from));
            }
        }
        if (dateTo != null && !dateTo.isBlank()) {
            Date to = parseDate(dateTo, true);
            if (to != null) {
                filters.add(Filters.lte(DATE_FIELD, to));
            }
        }
    }

    private void addTextSearchFilter(List<Bson> filters, String search, String... fields) {
        if (search != null && !search.isBlank()) {
            List<Bson> orFilters = new ArrayList<>();
            for (String field : fields) {
                orFilters.add(Filters.regex(field, search, "i"));
            }
            filters.add(Filters.or(orFilters));
        }
    }

    private void addExactFilter(List<Bson> filters, String field, String value) {
        if (value != null && !value.isBlank()) {
            filters.add(Filters.eq(field, value));
        }
    }

    private void addRegexFilter(List<Bson> filters, String field, String value) {
        if (value != null && !value.isBlank()) {
            filters.add(Filters.regex(field, value, "i"));
        }
    }

    private Date parseDate(String dateStr, boolean endOfDay) {
        try {
            LocalDate localDate = LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            Instant instant = endOfDay
                    ? localDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
                    : localDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            return Date.from(instant);
        } catch (Exception e) {
            log.warn("[Logging Tool] Invalid date format: {}", dateStr);
            return null;
        }
    }
}
