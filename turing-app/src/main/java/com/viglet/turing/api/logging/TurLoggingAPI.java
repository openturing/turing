package com.viglet.turing.api.logging;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/logging")
@Tag(name = "Logging", description = "Logging API")
public class TurLoggingAPI {
    public static final String DATE = "date";
    public static final String CONTENT = "content";
    public static final String TOTAL_ELEMENTS = "totalElements";
    public static final String TOTAL_PAGES = "totalPages";
    public static final String PAGE = "page";
    public static final String PAGE_SIZE = "pageSize";
    public static final String ENGINE_MONGODB = "mongodb";
    public static final String ENGINE_REDIS = "redis";

    private final String loggingEngine;
    private final boolean mongoEnabled;
    private final String mongoUri;
    private final String databaseName;
    private final String serverCollectionName;
    private final String indexingCollectionName;
    private final String aemCollectionName;

    public TurLoggingAPI(@Value("${turing.logging.engine:none}") String loggingEngine,
            @Value("${turing.mongodb.enabled:false}") boolean mongoEnabled,
            @Value("${turing.mongodb.uri:'mongodb://localhost:27017'}") String mongoUri,
            @Value("${turing.logging.database:'turingLog'}") String databaseName,
            @Value("${turing.logging.collection.server:'server'}") String serverCollectionName,
            @Value("${turing.logging.collection.indexing:'indexing'}") String indexingCollectionName,
            @Value("${turing.logging.collection.aem:'aem'}") String aemCollectionName) {
        this.loggingEngine = loggingEngine;
        this.mongoEnabled = mongoEnabled;
        this.mongoUri = mongoUri;
        this.databaseName = databaseName;
        this.serverCollectionName = serverCollectionName;
        this.indexingCollectionName = indexingCollectionName;
        this.aemCollectionName = aemCollectionName;
    }

    private boolean isMongoActive() {
        return ENGINE_MONGODB.equalsIgnoreCase(loggingEngine) && mongoEnabled;
    }

    @Operation(summary = "Server Logging")
    @GetMapping
    public Map<String, Object> serverLogging(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "asc") String sort) {
        return getGeneralDocuments(serverCollectionName, page, pageSize, level, dateFrom, dateTo, search, sort);
    }

    @Operation(summary = "Indexing Logging")
    @GetMapping("indexing")
    public Map<String, Object> indexingLogging(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String contentId,
            @RequestParam(required = false) String resultStatus,
            @RequestParam(required = false) String url,
            @RequestParam(defaultValue = "asc") String sort) {
        return getIndexingDocuments(indexingCollectionName, page, pageSize, dateFrom, dateTo,
                status, contentId, resultStatus, url, sort);
    }

    @Operation(summary = "AEM Logging")
    @GetMapping("aem")
    public Map<String, Object> aemLogging(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int pageSize,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "asc") String sort) {
        return getGeneralDocuments(aemCollectionName, page, pageSize, level, dateFrom, dateTo, search, sort);
    }

    private @NotNull Map<String, Object> getGeneralDocuments(String collectionName, int page, int pageSize,
            String level, String dateFrom, String dateTo,
            String search, String sort) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(CONTENT, Collections.emptyList());
        result.put(PAGE, page);
        result.put(PAGE_SIZE, pageSize);
        result.put(TOTAL_ELEMENTS, 0L);
        result.put(TOTAL_PAGES, 0);

        if (!isMongoActive()) {
            return result;
        }

        try (MongoClient mongoClient = MongoClients.create(mongoUri)) {
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            MongoCollection<Document> collection = database.getCollection(collectionName);

            List<Bson> filters = new ArrayList<>();
            addLevelFilter(filters, level);
            addDateRangeFilter(filters, dateFrom, dateTo);
            addTextSearchFilter(filters, search, "message", "stackTrace");

            Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);

            long totalElements = collection.countDocuments(filter);
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);

            Bson sorting = "desc".equalsIgnoreCase(sort) ? Sorts.descending(DATE) : Sorts.ascending(DATE);
            int skip = page * pageSize;
            List<Document> documentList = new ArrayList<>();
            collection.find(filter)
                    .sort(sorting)
                    .skip(skip)
                    .limit(pageSize)
                    .forEach(documentList::add);

            result.put(CONTENT, documentList);
            result.put(TOTAL_ELEMENTS, totalElements);
            result.put(TOTAL_PAGES, totalPages);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return result;
    }

    private @NotNull Map<String, Object> getIndexingDocuments(String collectionName, int page, int pageSize,
            String dateFrom, String dateTo,
            String status, String contentId,
            String resultStatus, String url, String sort) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(CONTENT, Collections.emptyList());
        result.put(PAGE, page);
        result.put(PAGE_SIZE, pageSize);
        result.put(TOTAL_ELEMENTS, 0L);
        result.put(TOTAL_PAGES, 0);

        if (!isMongoActive()) {
            return result;
        }

        try (MongoClient mongoClient = MongoClients.create(mongoUri)) {
            MongoDatabase database = mongoClient.getDatabase(databaseName);
            MongoCollection<Document> collection = database.getCollection(collectionName);

            List<Bson> filters = new ArrayList<>();
            addDateRangeFilter(filters, dateFrom, dateTo);
            addExactFilter(filters, "status", status);
            addRegexFilter(filters, "contentId", contentId);
            addExactFilter(filters, "resultStatus", resultStatus);
            addRegexFilter(filters, "url", url);

            Bson filter = filters.isEmpty() ? new Document() : Filters.and(filters);

            long totalElements = collection.countDocuments(filter);
            int totalPages = (int) Math.ceil((double) totalElements / pageSize);

            Bson sorting = "desc".equalsIgnoreCase(sort) ? Sorts.descending(DATE) : Sorts.ascending(DATE);
            int skip = page * pageSize;
            List<Document> documentList = new ArrayList<>();
            collection.find(filter)
                    .sort(sorting)
                    .skip(skip)
                    .limit(pageSize)
                    .forEach(documentList::add);

            result.put(CONTENT, documentList);
            result.put(TOTAL_ELEMENTS, totalElements);
            result.put(TOTAL_PAGES, totalPages);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return result;
    }

    private void addLevelFilter(List<Bson> filters, String level) {
        if (level != null && !level.isBlank()) {
            filters.add(Filters.eq("level", level.toUpperCase()));
        }
    }

    private void addDateRangeFilter(List<Bson> filters, String dateFrom, String dateTo) {
        if (dateFrom != null && !dateFrom.isBlank()) {
            Date from = parseDate(dateFrom, false);
            if (from != null) {
                filters.add(Filters.gte(DATE, from));
            }
        }
        if (dateTo != null && !dateTo.isBlank()) {
            Date to = parseDate(dateTo, true);
            if (to != null) {
                filters.add(Filters.lte(DATE, to));
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
            log.warn("Invalid date format: {}", dateStr);
            return null;
        }
    }
}
