package com.viglet.turing.genai.tool;

import com.viglet.turing.domain.sn.TurSNSiteFieldExtDomain;
import com.viglet.turing.domain.sn.TurSNSiteFieldExtRepositoryPort;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.sn.dsl.TurDslSearchResponse;
import com.viglet.turing.sn.dsl.TurDslSearchService;
import com.viglet.turing.sn.dsl.TurDslQueryRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Elasticsearch-compatible DSL Tool Calling service for Semantic Navigation.
 * <p>
 * Mirrors the tools from the official Elasticsearch MCP Server, adapted for
 * Turing ES multi-engine architecture (Solr, Lucene, Elasticsearch).
 * <p>
 * Tools:
 * <ul>
 *   <li>{@code dsl_list_indices} — list available SN sites (equivalent to ES indices)</li>
 *   <li>{@code dsl_get_mappings} — get field mappings for a site</li>
 *   <li>{@code dsl_search} — execute a full Elasticsearch Query DSL search</li>
 *   <li>{@code dsl_get_document} — retrieve a document by ID</li>
 *   <li>{@code dsl_suggest} — autocomplete and spell-check suggestions</li>
 * </ul>
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Service
public class TurDslToolService {

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteFieldExtRepositoryPort turSNSiteFieldExtRepositoryPort;
    private final TurDslSearchService turDslSearchService;
    private final TurSearchEnginePluginFactory pluginFactory;
    private final TurSNSiteConfigCache configCache;
    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    public TurDslToolService(TurSNSiteRepository turSNSiteRepository,
                              TurSNSiteLocaleRepository turSNSiteLocaleRepository,
                              TurSNSiteFieldExtRepositoryPort turSNSiteFieldExtRepositoryPort,
                              TurDslSearchService turDslSearchService,
                              TurSearchEnginePluginFactory pluginFactory,
                              TurSNSiteConfigCache configCache) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSNSiteFieldExtRepositoryPort = turSNSiteFieldExtRepositoryPort;
        this.turDslSearchService = turDslSearchService;
        this.pluginFactory = pluginFactory;
        this.configCache = configCache;
    }

    // ==================== Tool 1: List Indices ====================

    @Tool(name = "dsl_list_indices", description = ".")
    public String listIndices(String indexPattern) {
        log.info("[DSL Tool] dsl_list_indices called with pattern={}", indexPattern);

        List<TurSNSite> sites = turSNSiteRepository.findAll(Sort.by("name"));
        if (indexPattern != null && !indexPattern.isBlank()) {
            String pattern = indexPattern.replace("*", "").toLowerCase();
            sites = sites.stream()
                    .filter(s -> s.getName().toLowerCase().contains(pattern))
                    .toList();
        }

        if (sites.isEmpty()) {
            return "No indices found matching pattern: " + indexPattern;
        }

        List<Map<String, Object>> indices = new ArrayList<>();
        for (TurSNSite site : sites) {
            List<TurSNSiteLocale> locales = turSNSiteLocaleRepository.findByTurSNSite(site);
            String localeStr = locales.stream()
                    .map(l -> l.getLanguage().toString())
                    .collect(Collectors.joining(", "));
            String engineType = site.getTurSEInstance() != null
                    && site.getTurSEInstance().getTurSEVendor() != null
                    ? site.getTurSEInstance().getTurSEVendor().getTitle() : "unknown";

            Map<String, Object> index = new LinkedHashMap<>();
            index.put("index", site.getName());
            index.put("status", "open");
            index.put("engine", engineType);
            index.put("locales", localeStr);
            index.put("description", site.getDescription());
            indices.add(index);
        }

        try {
            return "Found %d indices.\n%s".formatted(indices.size(),
                    objectMapper.writeValueAsString(indices));
        } catch (Exception e) {
            return "Found %d indices.".formatted(indices.size());
        }
    }

    // ==================== Tool 2: Get Mappings ====================

    @Tool(name = "dsl_get_mappings", description = ".")
    public String getMappings(String index) {
        log.info("[DSL Tool] dsl_get_mappings called with index={}", index);

        if (index == null || index.isBlank()) {
            return "Index not found: " + index;
        }
        // T487 — memoize the immutable field-config read-model by lowercased
        // site name; on a hit the site + field queries inside loadFieldConfig
        // are skipped. Evicted on any SN write by TurSNSiteSnapshotEvictionListener.
        TurSNSiteFieldConfigModel model = configCache.fieldConfig(index.toLowerCase(),
                () -> loadFieldConfig(index));
        if (model == null) {
            return "Index not found: " + index;
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        for (TurSNSiteFieldConfigModel.FieldInfo field : model.fields()) {
            properties.put(field.name(), buildFieldMapping(field));
        }
        Map<String, Object> mappings = new LinkedHashMap<>();
        mappings.put(model.siteName(), Map.of("mappings", Map.of("properties", properties)));

        try {
            return objectMapper.writeValueAsString(mappings);
        } catch (Exception e) {
            return "Found %d fields for index '%s'.".formatted(model.fields().size(), index);
        }
    }

    /**
     * Loads the enabled-field configuration for {@code index} into the immutable
     * {@link TurSNSiteFieldConfigModel} read-model, or {@code null} when the site
     * does not exist. Invoked only on a {@link TurSNSiteConfigCache} miss.
     */
    private TurSNSiteFieldConfigModel loadFieldConfig(String index) {
        return turSNSiteRepository.findByNameIgnoreCase(index)
                .map(site -> {
                    // Enabled fields read through the domain port — the derived
                    // FieldInfo list feeds an immutable cached read-model
                    // (TurSNSiteConfigCache), so it must not carry JPA entities
                    // (T542 boundary: cache read-models go through records).
                    List<TurSNSiteFieldExtDomain> fields = turSNSiteFieldExtRepositoryPort
                            .findBySnSiteIdAndEnabled(site.getId(), 1);
                    List<TurSNSiteFieldConfigModel.FieldInfo> infos = fields.stream()
                            .map(f -> new TurSNSiteFieldConfigModel.FieldInfo(
                                    f.name(),
                                    f.type() != null ? f.type().toString().toLowerCase() : "text",
                                    f.isFacet(),
                                    f.isMultiValued(),
                                    f.description()))
                            .toList();
                    return new TurSNSiteFieldConfigModel(site.getName(), infos);
                })
                .orElse(null);
    }

    /** Builds the mapping entry (type + optional facet/multi_valued/description) for one field. */
    private Map<String, Object> buildFieldMapping(TurSNSiteFieldConfigModel.FieldInfo field) {
        Map<String, Object> fieldMapping = new LinkedHashMap<>();
        fieldMapping.put("type", field.type() != null ? field.type() : "text");
        if (field.facet()) fieldMapping.put("facet", true);
        if (field.multiValued()) fieldMapping.put("multi_valued", true);
        if (field.description() != null) {
            fieldMapping.put("description", field.description());
        }
        return fieldMapping;
    }

    // ==================== Tool 3: DSL Search ====================

    @Tool(name = "dsl_search", description = ".")
    public String search(String index, String locale, String queryBody) {
        log.info("[DSL Tool] dsl_search called: index={}, locale={}, body={}",
                index, locale, queryBody);

        if (index == null || index.isBlank()) {
            return "Error: 'index' parameter is required.";
        }
        if (queryBody == null || queryBody.isBlank()) {
            queryBody = "{\"query\":{\"match_all\":{}}}";
        }

        try {
            TurDslQueryRequest request = objectMapper.readValue(queryBody,
                    TurDslQueryRequest.class);
            String searchLocale = (locale != null && !locale.isBlank()) ? locale : "en";

            Optional<TurDslSearchResponse> responseOpt =
                    turDslSearchService.search(index, searchLocale, request);

            if (responseOpt.isEmpty()) {
                return "No results. Site '%s' with locale '%s' may not exist.".formatted(
                        index, searchLocale);
            }

            return formatSearchResponse(responseOpt.get());
        } catch (Exception e) {
            log.error("[DSL Tool] dsl_search failed", e);
            return "Error executing DSL search: " + e.getMessage()
                    + "\nEnsure the query_body is valid Elasticsearch Query DSL JSON.";
        }
    }

    /** Renders a search response into the human-readable tool reply (hits + aggs + suggest). */
    private String formatSearchResponse(TurDslSearchResponse response) {
        StringBuilder sb = new StringBuilder();
        long total = response.hits() != null && response.hits().total() != null
                ? response.hits().total().value() : 0;
        int shown = response.hits() != null && response.hits().hits() != null
                ? response.hits().hits().size() : 0;

        sb.append("Found %d results (showing %d). Took %dms.\n".formatted(
                total, shown, response.took()));

        // Hits
        if (response.hits() != null && response.hits().hits() != null) {
            sb.append(toJsonOrError(
                    response.hits().hits().stream()
                            .map(hit -> {
                                Map<String, Object> doc = new LinkedHashMap<>();
                                doc.put("_id", hit.id());
                                doc.put("_score", hit.score());
                                if (hit.source() != null) doc.putAll(hit.source());
                                return doc;
                            }).toList(),
                    "[Error serializing hits]"));
        }

        // Aggregations
        if (response.aggregations() != null && !response.aggregations().isEmpty()) {
            sb.append("\n\nAggregations:\n");
            sb.append(toJsonOrError(response.aggregations(), "[Error serializing aggregations]"));
        }

        // Suggest
        if (response.suggest() != null && !response.suggest().isEmpty()) {
            sb.append("\n\nSuggestions:\n");
            sb.append(toJsonOrError(response.suggest(), "[Error serializing suggestions]"));
        }

        return sb.toString();
    }

    /** Builds the cat-shards style row for one site+locale (doc count + store size via the SE plugin). */
    private Map<String, Object> buildShardRow(TurSNSite site, TurSNSiteLocale locale) {
        var plugin = pluginFactory.getPluginForSite(site);
        var seInstance = site.getTurSEInstance();
        String coreName = locale.getCore();

        // Get document count via plugin
        long docCount;
        try {
            docCount = plugin.getDocumentTotal(locale);
        } catch (Exception e) {
            docCount = -1;
        }

        // List indexes/cores for store info
        String storeSize = "N/A";
        try {
            for (var info : plugin.listIndexes(seInstance)) {
                if (coreName.equalsIgnoreCase(info.name())) {
                    storeSize = info.numDocs() + " docs";
                    break;
                }
            }
        } catch (Exception e) {
            // ignore
        }

        Map<String, Object> shard = new LinkedHashMap<>();
        shard.put("index", site.getName());
        shard.put("shard", coreName);
        shard.put("prirep", "p");
        shard.put("state", "STARTED");
        shard.put("docs", docCount >= 0 ? docCount : "unknown");
        shard.put("store", storeSize);
        shard.put("engine", plugin.getPluginType());
        shard.put("node", seInstance != null ? seInstance.getEndpointUrl() : "embedded");
        shard.put("locale", locale.getLanguage().toString());
        return shard;
    }

    /** Serializes {@code value} to JSON, or returns {@code errorPlaceholder} on failure. */
    private String toJsonOrError(Object value, String errorPlaceholder) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return errorPlaceholder;
        }
    }

    // ==================== Tool 4: Get Document ====================

    @Tool(name = "dsl_get_document", description = ".")
    public String getDocument(String index, String locale, String documentId) {
        log.info("[DSL Tool] dsl_get_document called: index={}, locale={}, id={}",
                index, locale, documentId);

        String queryBody = "{\"query\":{\"ids\":{\"values\":[\"%s\"]}},\"size\":1}".formatted(documentId);

        try {
            TurDslQueryRequest request = objectMapper.readValue(queryBody,
                    TurDslQueryRequest.class);
            String searchLocale = (locale != null && !locale.isBlank()) ? locale : "en";

            Optional<TurDslSearchResponse> responseOpt =
                    turDslSearchService.search(index, searchLocale, request);

            if (responseOpt.isEmpty() || responseOpt.get().hits() == null
                    || responseOpt.get().hits().hits() == null
                    || responseOpt.get().hits().hits().isEmpty()) {
                return "Document not found with ID: " + documentId;
            }

            var hit = responseOpt.get().hits().hits().getFirst();
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("_id", hit.id());
            if (hit.source() != null) doc.putAll(hit.source());

            return objectMapper.writeValueAsString(doc);
        } catch (Exception e) {
            log.error("[DSL Tool] dsl_get_document failed", e);
            return "Error retrieving document: " + e.getMessage();
        }
    }

    // ==================== Tool 5: Suggest ====================

    @Tool(name = "dsl_suggest", description = ".")
    public String suggest(String index, String locale, String text) {
        log.info("[DSL Tool] dsl_suggest called: index={}, locale={}, text={}",
                index, locale, text);

        String queryBody = """
                {
                  "query":{"match":{"_text_":"%s"}},
                  "size":5,
                  "suggest":{
                    "text-suggest":{
                      "text":"%s",
                      "term":{"field":"title","size":5}
                    }
                  }
                }""".formatted(text, text);

        try {
            TurDslQueryRequest request = objectMapper.readValue(queryBody,
                    TurDslQueryRequest.class);
            String searchLocale = (locale != null && !locale.isBlank()) ? locale : "en";

            Optional<TurDslSearchResponse> responseOpt =
                    turDslSearchService.search(index, searchLocale, request);

            StringBuilder sb = new StringBuilder();

            if (responseOpt.isPresent()) {
                TurDslSearchResponse response = responseOpt.get();
                appendSearchResults(sb, response, text);
                appendSuggestions(sb, response);
            }

            if (sb.isEmpty()) {
                return "No suggestions found for: " + text;
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("[DSL Tool] dsl_suggest failed", e);
            return "Error getting suggestions: " + e.getMessage();
        }
    }

    /**
     * Appends the hit list (title + id per hit) of a suggest response, if any.
     *
     * @since 2026.3.1
     */
    private static void appendSearchResults(StringBuilder sb, TurDslSearchResponse response, String text) {
        if (response.hits() == null || response.hits().hits() == null
                || response.hits().hits().isEmpty()) {
            return;
        }
        sb.append("Search results for '%s':\n".formatted(text));
        for (var hit : response.hits().hits()) {
            if (hit.source() != null) {
                sb.append("  - ");
                if (hit.source().containsKey("title")) {
                    sb.append(hit.source().get("title"));
                }
                sb.append(" (id: ").append(hit.id()).append(")\n");
            }
        }
    }

    /**
     * Appends the term-suggester options (text + score) of a suggest response,
     * if any.
     *
     * @since 2026.3.1
     */
    private static void appendSuggestions(StringBuilder sb, TurDslSearchResponse response) {
        if (response.suggest() == null || response.suggest().isEmpty()) {
            return;
        }
        sb.append("\nSuggestions:\n");
        for (var entry : response.suggest().entrySet()) {
            for (var suggestion : entry.getValue()) {
                if (suggestion.options() != null) {
                    for (var option : suggestion.options()) {
                        sb.append("  - ").append(option.text())
                                .append(" (score: ").append(option.score())
                                .append(")\n");
                    }
                }
            }
        }
    }

    // ==================== Tool 6: Get Shards ====================

    @Tool(name = "dsl_get_shards", description = ".")
    public String getShards(String index) {
        log.info("[DSL Tool] dsl_get_shards called with index={}", index);

        List<TurSNSite> sites;
        if (index != null && !index.isBlank() && !"*".equals(index)) {
            sites = turSNSiteRepository.findByNameIgnoreCase(index)
                    .map(List::of).orElse(List.of());
        } else {
            sites = turSNSiteRepository.findAll(Sort.by("name"));
        }

        if (sites.isEmpty()) {
            return "No indices found" + (index != null ? " for: " + index : ".");
        }

        List<Map<String, Object>> shards = new ArrayList<>();
        for (TurSNSite site : sites) {
            for (TurSNSiteLocale locale : turSNSiteLocaleRepository.findByTurSNSite(site)) {
                shards.add(buildShardRow(site, locale));
            }
        }

        try {
            return "Found %d shards.\n%s".formatted(shards.size(),
                    objectMapper.writeValueAsString(shards));
        } catch (Exception e) {
            return "Found %d shards.".formatted(shards.size());
        }
    }
}
