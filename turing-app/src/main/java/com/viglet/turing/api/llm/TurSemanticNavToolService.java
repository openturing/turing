package com.viglet.turing.api.llm;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.net.URIBuilder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.viglet.turing.client.sn.HttpTurSNServer;
import com.viglet.turing.client.sn.TurSNDocumentList;
import com.viglet.turing.client.sn.TurSNQuery;
import com.viglet.turing.client.sn.autocomplete.TurSNAutoCompleteQuery;
import com.viglet.turing.client.sn.facet.TurSNFacetField;
import com.viglet.turing.client.sn.facet.TurSNFacetFieldList;
import com.viglet.turing.client.sn.facet.TurSNFacetFieldValue;
import com.viglet.turing.client.sn.response.QueryTurSNResponse;
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
public class TurSemanticNavToolService {

    private static final Set<String> SUMMARY_FIELDS = Set.of(
            "id", "title", "url", "description", "date", "type",
            "modification_date", "publication_date", "author", "source_apps");

    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

    @Value("${server.port:2700}")
    private int serverPort;

    public TurSemanticNavToolService(TurSNSiteRepository turSNSiteRepository,
            TurSNSiteLocaleRepository turSNSiteLocaleRepository,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
    }

    @Tool(name = "list_sites", description = """
            Lists available Semantic Navigation sites.
            ONLY use this if you do not know the siteName or need to verify available locales.
            Args:
                filterName (str): Optional. Filter sites by name. Use empty string for all.""")
    public String listSites(String filterName) {
        log.info("[SemanticNav Tool] list_sites called with filterName={}", filterName);
        List<TurSNSite> sites = turSNSiteRepository.findAll(Sort.by("name"));
        if (filterName != null && !filterName.isBlank()) {
            String filter = filterName.toLowerCase();
            sites = sites.stream()
                    .filter(s -> s.getName().toLowerCase().contains(filter))
                    .toList();
        }
        if (sites.isEmpty()) {
            log.warn("[SemanticNav Tool] list_sites: No sites found");
            return "No Semantic Navigation sites found.";
        }
        StringBuilder sb = new StringBuilder("site_name;description;locales\n");
        for (TurSNSite site : sites) {
            List<TurSNSiteLocale> locales = turSNSiteLocaleRepository.findByTurSNSite(site);
            String localeStr = locales.stream()
                    .map(l -> l.getLanguage().toString())
                    .collect(Collectors.joining(", "));
            sb.append(site.getName()).append(";")
                    .append(site.getDescription() != null ? site.getDescription() : "").append(";")
                    .append(localeStr).append("\n");
        }
        String result = sb.toString();
        log.info("[SemanticNav Tool] list_sites returned {} sites:\n{}", sites.size(), result);
        return result;
    }

    @Tool(name = "get_site_fields", description = """
            Retrieves valid facet fields for filtering a specific site.
            ONLY call this if the user asks for specific filters (e.g., 'by author', 'only news')
            and you need to know the exact field name to use in search_site.
            Args:
                siteName (str): Name of the site.""")
    public String getSiteFields(String siteName) {
        log.info("[SemanticNav Tool] get_site_fields called with siteName={}", siteName);
        return turSNSiteRepository.findByName(siteName)
                .map(site -> {
                    List<TurSNSiteFieldExt> fields = turSNSiteFieldExtRepository
                            .findByTurSNSiteAndEnabled(site, 1);
                    if (fields.isEmpty()) {
                        return "No enabled fields found for site: " + siteName;
                    }
                    log.info("[SemanticNav Tool] get_site_fields: found {} enabled fields for site {}", fields.size(),
                            siteName);
                    StringBuilder sb = new StringBuilder("field_name;type;facet;description\n");
                    for (TurSNSiteFieldExt field : fields) {
                        sb.append(field.getName()).append(";")
                                .append(field.getType()).append(";")
                                .append(field.getFacet() == 1 ? "yes" : "no").append(";")
                                .append(field.getDescription() != null ? field.getDescription() : "")
                                .append("\n");
                    }
                    String result = sb.toString();
                    log.info("[SemanticNav Tool] get_site_fields output ({} chars):\n{}", result.length(), result);
                    return result;
                })
                .orElse("Site not found: " + siteName);
    }

    @Tool(name = "get_valid_filter_values", description = """
            Returns the exact, valid string values that can be used for a specific filter (facet) in search_site.
            Use this tool BEFORE applying a filter in search_site if you are not 100% sure of the exact value name
            in the database. This prevents hallucinating filter values.
            Args:
                siteName (str): Required. Name of the site.
                locale (str): Required. Use 'pt_BR' or 'en'.
                fieldName (str): Required. The facet field name (e.g., 'temas', 'author', 'type').""")
    public String getValidFilterValues(String siteName, String locale, String fieldName) {
        log.info("[SemanticNav Tool] get_valid_filter_values called: siteName={}, locale={}, fieldName={}",
                siteName, locale, fieldName);
        try {
            HttpTurSNServer turSNServer = new HttpTurSNServer(
                    URI.create("http://localhost:" + serverPort),
                    siteName, LocaleUtils.toLocale(locale));

            TurSNQuery turSNQuery = new TurSNQuery();
            turSNQuery.setQuery("*");
            turSNQuery.setRows(1);
            turSNQuery.setPageNumber(1);

            QueryTurSNResponse response = turSNServer.query(turSNQuery);
            TurSNFacetFieldList facetFields = response.getFacetFields();

            if (facetFields == null) {
                return "No facet data available for site: " + siteName;
            }

            for (TurSNFacetField facetField : facetFields) {
                if (facetField.getName() != null && facetField.getName().equalsIgnoreCase(fieldName)) {
                    List<TurSNFacetFieldValue> values = facetField.getValues().getTurSNFacetFieldValues();
                    if (values == null || values.isEmpty()) {
                        return "No values found for facet field: " + fieldName;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append("Valid values for '").append(fieldName).append("':\n");
                    sb.append("value;count\n");
                    for (TurSNFacetFieldValue value : values) {
                        sb.append(value.getLabel()).append(";")
                                .append(value.getCount()).append("\n");
                    }
                    String result = sb.toString();
                    log.info("[SemanticNav Tool] get_valid_filter_values result:\n{}", result);
                    return result;
                }
            }

            StringBuilder availableFacets = new StringBuilder();
            availableFacets.append("Field '").append(fieldName)
                    .append("' is not a valid facet. Available facet fields: ");
            for (TurSNFacetField facetField : facetFields) {
                availableFacets.append(facetField.getName()).append(", ");
            }
            return availableFacets.toString();
        } catch (Exception e) {
            log.error("[SemanticNav Tool] get_valid_filter_values failed", e);
            return "Error retrieving filter values. Check if the site name and field are correct.";
        }
    }

    @Tool(name = "search_site", description = """
            Searches for content on a site. Returns compact results (ID, title, URL, snippet).
            Use get_document_details to read the full content of a specific result.
            ACTION REQUIRED: If the user asks to search, call this tool IMMEDIATELY.

            CRITICAL RULES FOR FILTERING (filterQueries):
            1. DO NOT invent or guess values for filters. Call get_valid_filter_values first.
            2. If the user asks for a general topic (e.g., "educaction", "Machine Learning"), put it in 'query'!
            3. ONLY use filterQueries with EXACT values confirmed by get_valid_filter_values.

            Args:
                siteName (str): Required. Use 'educaction-stage-publish' as default if unknown.
                locale (str): Required. Use 'pt_BR' or 'en'.
                query (str): Required. The main search text. Put all general keywords here.
                rows (int): Optional. Number of results. Defaults to 10.
                page (int): Optional. Defaults to 1.
                filterQueries (str): Optional. Format: field_name:VALUE. Separate with pipe (|).
            """)
    public String searchSite(String siteName, String locale, String query,
            Integer rows, Integer page, String filterQueries) {

        log.info("[SemanticNav Tool] search_site called: siteName={}, locale={}, query='{}', fq={}",
                siteName, locale, query, filterQueries);

        try {
            HttpTurSNServer turSNServer = new HttpTurSNServer(
                    URI.create("http://localhost:" + serverPort),
                    siteName, LocaleUtils.toLocale(locale));

            TurSNQuery turSNQuery = new TurSNQuery();
            turSNQuery.setQuery((query != null && !query.isBlank()) ? query : "*");

            if (filterQueries != null && !filterQueries.isBlank()) {
                String[] fqs = filterQueries.split("\\|");
                turSNQuery.addFilterQuery(fqs);
            }

            turSNQuery.setRows(rows != null && rows > 0 ? rows : 10);
            turSNQuery.setPageNumber(page != null && page > 0 ? page : 1);
            turSNQuery.setSortField(TurSNQuery.Order.asc);

            QueryTurSNResponse response = turSNServer.query(turSNQuery);
            TurSNDocumentList results = response.getResults();

            if (results == null || results.getTurSNDocuments() == null || results.getTurSNDocuments().isEmpty()) {
                String noResultMsg = "No results found for query: " + query
                        + ". Try calling get_search_suggestions to check for spelling errors.";
                if (filterQueries != null && !filterQueries.isBlank()) {
                    noResultMsg += " Also verify filter values with get_valid_filter_values.";
                }
                return noResultMsg;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(results.getQueryContext() != null
                    ? results.getQueryContext().getCount()
                    : results.getTurSNDocuments().size())
                    .append(" results for query: ").append(query).append("\n\n");

            int idx = 0;
            for (var doc : results.getTurSNDocuments()) {
                idx++;
                sb.append("--- Result ").append(idx).append(" ---\n");
                if (doc.getContent() != null && doc.getContent().getFields() != null) {
                    Map<String, Object> fields = doc.getContent().getFields();
                    for (String key : SUMMARY_FIELDS) {
                        Object value = fields.get(key);
                        if (value != null) {
                            String valStr = value.toString();
                            if (valStr.length() > 300) {
                                valStr = valStr.substring(0, 300) + "...";
                            }
                            sb.append(key).append(": ").append(valStr).append("\n");
                        }
                    }
                    for (Map.Entry<String, Object> entry : fields.entrySet()) {
                        if (!SUMMARY_FIELDS.contains(entry.getKey()) && entry.getValue() != null) {
                            String valStr = entry.getValue().toString();
                            if (!valStr.isBlank() && valStr.length() <= 150) {
                                sb.append(entry.getKey()).append(": ").append(valStr).append("\n");
                            }
                        }
                    }
                }
                sb.append("\n");
            }

            sb.append("TIP: Use get_document_details with a document ID to read full content.");
            return sb.toString();
        } catch (Exception e) {
            log.error("[SemanticNav Tool] search_site failed", e);
            return "Error executing search. Suggest to the user to try again.";
        }
    }

    @Tool(name = "get_document_details", description = """
            Retrieves the full text and complete metadata of a single document using its ID.
            Call this tool when the user asks for specific details about a document found in a
            previous search, or if you need to read the full content to answer a complex question.
            Args:
                siteName (str): Required. Name of the site.
                locale (str): Required. Use 'pt_BR' or 'en'.
                documentId (str): Required. The document ID from a previous search result.""")
    public String getDocumentDetails(String siteName, String locale, String documentId) {
        log.info("[SemanticNav Tool] get_document_details called: siteName={}, locale={}, documentId={}",
                siteName, locale, documentId);
        try {
            HttpTurSNServer turSNServer = new HttpTurSNServer(
                    URI.create("http://localhost:" + serverPort),
                    siteName, LocaleUtils.toLocale(locale));

            TurSNQuery turSNQuery = new TurSNQuery();
            turSNQuery.setQuery("id:\"" + documentId + "\"");
            turSNQuery.setRows(1);
            turSNQuery.setPageNumber(1);

            QueryTurSNResponse response = turSNServer.query(turSNQuery);
            TurSNDocumentList results = response.getResults();

            if (results == null || results.getTurSNDocuments() == null || results.getTurSNDocuments().isEmpty()) {
                return "Document not found with ID: " + documentId;
            }

            var doc = results.getTurSNDocuments().getFirst();
            if (doc.getContent() == null || doc.getContent().getFields() == null) {
                return "Document found but has no content. ID: " + documentId;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== Full Document Details ===\n");
            doc.getContent().getFields().forEach((key, value) -> {
                String valStr = value != null ? value.toString() : "";
                if (valStr.length() > 5000) {
                    valStr = valStr.substring(0, 5000) + "... [TRUNCATED - content too long]";
                }
                sb.append(key).append(": ").append(valStr).append("\n");
            });

            return sb.toString();
        } catch (Exception e) {
            log.error("[SemanticNav Tool] get_document_details failed", e);
            return "Error retrieving document details. Check if the document ID is correct.";
        }
    }

    @Tool(name = "get_search_suggestions", description = """
            Provides autocomplete suggestions and spelling corrections for a search query.
            Use this tool ONLY if search_site returns 0 results, to check if the user misspelled a word.
            Also useful to help the user refine their search terms.
            Args:
                siteName (str): Required. Name of the site.
                locale (str): Required. Use 'pt_BR' or 'en'.
                term (str): Required. The search term to get suggestions for.""")
    public String getSearchSuggestions(String siteName, String locale, String term) {
        log.info("[SemanticNav Tool] get_search_suggestions called: siteName={}, locale={}, term={}",
                siteName, locale, term);
        try {
            Locale parsedLocale = LocaleUtils.toLocale(locale);
            HttpTurSNServer turSNServer = new HttpTurSNServer(
                    URI.create("http://localhost:" + serverPort),
                    siteName, parsedLocale);

            StringBuilder sb = new StringBuilder();

            // 1. Get autocomplete suggestions
            TurSNAutoCompleteQuery autoCompleteQuery = new TurSNAutoCompleteQuery();
            autoCompleteQuery.setQuery(term);
            autoCompleteQuery.setRows(10);

            List<String> suggestions = turSNServer.autoComplete(autoCompleteQuery);
            if (suggestions != null && !suggestions.isEmpty()) {
                sb.append("Autocomplete suggestions for '").append(term).append("':\n");
                for (String suggestion : suggestions) {
                    sb.append("  - ").append(suggestion).append("\n");
                }
            } else {
                sb.append("No autocomplete suggestions found for '").append(term).append("'.\n");
            }

            // 2. Check spell check / did-you-mean via a search query
            TurSNQuery turSNQuery = new TurSNQuery();
            turSNQuery.setQuery(term);
            turSNQuery.setRows(1);
            turSNQuery.setPageNumber(1);

            QueryTurSNResponse response = turSNServer.query(turSNQuery);
            if (response.getDidYouMean() != null && response.getDidYouMean().isCorrectedText()) {
                sb.append("\nDid you mean: '")
                        .append(response.getDidYouMean().getCorrected().getText())
                        .append("'?\n");
            }

            if (sb.isEmpty()) {
                return "No suggestions or corrections found for: " + term;
            }

            String result = sb.toString();
            log.info("[SemanticNav Tool] get_search_suggestions result:\n{}", result);
            return result;
        } catch (Exception e) {
            log.error("[SemanticNav Tool] get_search_suggestions failed", e);
            return "Error retrieving suggestions. Check if the site name is correct.";
        }
    }

    @Tool(name = "find_similar_documents", description = """
            Finds documents that are semantically similar to a specific document (More Like This).
            Use this when the user asks for recommendations, related articles, or 'more like this'.
            Args:
                siteName (str): Required. Name of the site.
                locale (str): Required. Use 'pt_BR' or 'en'.
                documentId (str): Required. The document ID to find similar documents for.""")
    public String findSimilarDocuments(String siteName, String locale, String documentId) {
        log.info("[SemanticNav Tool] find_similar_documents called: siteName={}, locale={}, documentId={}",
                siteName, locale, documentId);
        try {
            // The Java SDK does not expose MLT (widget.similar) results directly,
            // so we make a direct HTTP call to the search API and parse the raw response.
            String searchUrl = String.format("http://localhost:%d/api/sn/%s/search", serverPort, siteName);
            URIBuilder uriBuilder = new URIBuilder(searchUrl)
                    .addParameter(TurSNParamType.QUERY, "id:\"" + documentId + "\"")
                    .addParameter(TurSNParamType.LOCALE, locale)
                    .addParameter(TurSNParamType.ROWS, "1")
                    .addParameter(TurSNParamType.PAGE, "1");

            HttpGet httpGet = new HttpGet(uriBuilder.build());
            httpGet.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
            httpGet.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            httpGet.setHeader(HttpHeaders.ACCEPT_ENCODING, StandardCharsets.UTF_8.name());

            String jsonResponse;
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                jsonResponse = client.execute(httpGet,
                        response -> EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            }

            if (jsonResponse == null || jsonResponse.isBlank()) {
                return "No response from search API for document ID: " + documentId;
            }

            TurSNSiteSearchBean searchBean = new ObjectMapper()
                    .readValue(jsonResponse, TurSNSiteSearchBean.class);

            if (searchBean.getWidget() == null || searchBean.getWidget().getSimilar() == null
                    || searchBean.getWidget().getSimilar().isEmpty()) {
                return "No similar documents found for document ID: " + documentId
                        + ". The document may not have enough indexed content for similarity matching.";
            }

            List<TurSESimilarResult> similarResults = searchBean.getWidget().getSimilar();
            StringBuilder sb = new StringBuilder();
            sb.append("Found ").append(similarResults.size())
                    .append(" similar documents for ID: ").append(documentId).append("\n\n");

            int idx = 0;
            for (TurSESimilarResult similar : similarResults) {
                idx++;
                sb.append("--- Similar ").append(idx).append(" ---\n");
                if (similar.getId() != null)
                    sb.append("id: ").append(similar.getId()).append("\n");
                if (similar.getTitle() != null)
                    sb.append("title: ").append(similar.getTitle()).append("\n");
                if (similar.getType() != null)
                    sb.append("type: ").append(similar.getType()).append("\n");
                if (similar.getUrl() != null)
                    sb.append("url: ").append(similar.getUrl()).append("\n");
                sb.append("\n");
            }

            sb.append("TIP: Use get_document_details with a document ID to read full content.");
            return sb.toString();
        } catch (Exception e) {
            log.error("[SemanticNav Tool] find_similar_documents failed", e);
            return "Error finding similar documents. Check if the document ID is correct.";
        }
    }

    @Tool(name = "get_aggregated_stats", description = """
            Calculates totals and distributions for a specific category using facet aggregation.
            Call this when the user asks 'how many', 'what is the distribution', 'count by category',
            or wants statistical data about content WITHOUT needing to read the actual documents.
            Much more efficient than reading all results and counting manually.
            Args:
                siteName (str): Required. Name of the site.
                locale (str): Required. Use 'pt_BR' or 'en'.
                query (str): Required. The search query to scope the aggregation (e.g., 'Pos-Graduacao', '*' for all).
                facetField (str): Required. The field to aggregate by (e.g., 'area_conhecimento', 'type', 'author').
                filterQueries (str): Optional. Additional filters. Format: field_name:VALUE. Separate with pipe (|).""")
    public String getAggregatedStats(String siteName, String locale, String query,
            String facetField, String filterQueries) {
        log.info(
                "[SemanticNav Tool] get_aggregated_stats called: siteName={}, locale={}, query='{}', facetField={}, fq={}",
                siteName, locale, query, facetField, filterQueries);
        try {
            HttpTurSNServer turSNServer = new HttpTurSNServer(
                    URI.create("http://localhost:" + serverPort),
                    siteName, LocaleUtils.toLocale(locale));

            TurSNQuery turSNQuery = new TurSNQuery();
            turSNQuery.setQuery((query != null && !query.isBlank()) ? query : "*");
            turSNQuery.setRows(1);
            turSNQuery.setPageNumber(1);

            if (filterQueries != null && !filterQueries.isBlank()) {
                String[] fqs = filterQueries.split("\\|");
                turSNQuery.addFilterQuery(fqs);
            }

            QueryTurSNResponse response = turSNServer.query(turSNQuery);

            long totalCount = 0;
            if (response.getResults() != null && response.getResults().getQueryContext() != null) {
                totalCount = response.getResults().getQueryContext().getCount();
            }

            TurSNFacetFieldList facetFields = response.getFacetFields();
            if (facetFields == null) {
                return "No facet data available. Total documents matching query '" + query + "': " + totalCount;
            }

            for (TurSNFacetField ff : facetFields) {
                if (ff.getName() != null && ff.getName().equalsIgnoreCase(facetField)) {
                    List<TurSNFacetFieldValue> values = ff.getValues().getTurSNFacetFieldValues();
                    if (values == null || values.isEmpty()) {
                        return "No aggregation data for field '" + facetField
                                + "'. Total documents matching query '" + query + "': " + totalCount;
                    }

                    StringBuilder sb = new StringBuilder();
                    sb.append("=== Aggregated Stats ===\n");
                    sb.append("Query: '").append(query).append("'\n");
                    sb.append("Total matching documents: ").append(totalCount).append("\n");
                    sb.append("Distribution by '").append(facetField).append("':\n\n");
                    sb.append("category;count\n");
                    for (TurSNFacetFieldValue value : values) {
                        sb.append(value.getLabel()).append(";").append(value.getCount()).append("\n");
                    }
                    String result = sb.toString();
                    log.info("[SemanticNav Tool] get_aggregated_stats result:\n{}", result);
                    return result;
                }
            }

            StringBuilder availableFacets = new StringBuilder();
            availableFacets.append("Field '").append(facetField)
                    .append("' is not a valid facet. Available facet fields for aggregation: ");
            for (TurSNFacetField ff : facetFields) {
                availableFacets.append(ff.getName()).append(", ");
            }
            availableFacets.append("\nTotal documents matching query '").append(query).append("': ").append(totalCount);
            return availableFacets.toString();
        } catch (Exception e) {
            log.error("[SemanticNav Tool] get_aggregated_stats failed", e);
            return "Error calculating aggregated stats. Check if the site name and facet field are correct.";
        }
    }

    @Tool(name = "get_document_highlights", description = """
            Extracts specific sentences or paragraphs from a document where the search terms appear.
            Use this for precise questions about long documents (rules, regulations, chapters) to save context.
            Instead of reading the full document, this returns only the relevant highlighted passages.
            Requires highlighting to be enabled on the site.
            Args:
                siteName (str): Required. Name of the site.
                locale (str): Required. Use 'pt_BR' or 'en'.
                documentId (str): Required. The document ID to search within.
                termsToHighlight (str): Required. The search terms to find within the document.""")
    public String getDocumentHighlights(String siteName, String locale,
            String documentId, String termsToHighlight) {
        log.info("[SemanticNav Tool] get_document_highlights called: siteName={}, locale={}, docId={}, terms='{}'",
                siteName, locale, documentId, termsToHighlight);
        try {
            // Search with the highlight terms as query and filter by document ID.
            // The search engine returns fields with highlighted passages (HL tags applied).
            String searchUrl = String.format("http://localhost:%d/api/sn/%s/search", serverPort, siteName);
            URIBuilder uriBuilder = new URIBuilder(searchUrl)
                    .addParameter(TurSNParamType.QUERY, termsToHighlight)
                    .addParameter(TurSNParamType.LOCALE, locale)
                    .addParameter(TurSNParamType.FILTER_QUERIES_DEFAULT, "id:\"" + documentId + "\"")
                    .addParameter(TurSNParamType.ROWS, "1")
                    .addParameter(TurSNParamType.PAGE, "1");

            HttpGet httpGet = new HttpGet(uriBuilder.build());
            httpGet.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
            httpGet.setHeader(HttpHeaders.CONTENT_TYPE, ContentType.APPLICATION_JSON.getMimeType());
            httpGet.setHeader(HttpHeaders.ACCEPT_ENCODING, StandardCharsets.UTF_8.name());

            String jsonResponse;
            try (CloseableHttpClient client = HttpClients.createDefault()) {
                jsonResponse = client.execute(httpGet,
                        response -> EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8));
            }

            if (jsonResponse == null || jsonResponse.isBlank()) {
                return "No response from search API for document ID: " + documentId;
            }

            TurSNSiteSearchBean searchBean = new ObjectMapper()
                    .readValue(jsonResponse, TurSNSiteSearchBean.class);

            if (searchBean.getResults() == null || searchBean.getResults().getDocument() == null
                    || searchBean.getResults().getDocument().isEmpty()) {
                return "Document not found with ID: " + documentId
                        + " or the terms '" + termsToHighlight + "' do not appear in this document.";
            }

            var docBean = searchBean.getResults().getDocument().getFirst();
            if (docBean.getFields() == null) {
                return "Document found but has no content fields.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== Highlighted Passages ===\n");
            sb.append("Document ID: ").append(documentId).append("\n");
            sb.append("Search terms: '").append(termsToHighlight).append("'\n\n");

            boolean foundHighlight = false;
            for (Map.Entry<String, Object> entry : docBean.getFields().entrySet()) {
                if (entry.getValue() == null)
                    continue;
                String valStr = entry.getValue().toString();
                // Check if the field contains highlighting tags (em, b, strong, mark)
                if (valStr.contains("<em>") || valStr.contains("<b>")
                        || valStr.contains("<strong>") || valStr.contains("<mark>")) {
                    foundHighlight = true;
                    if (valStr.length() > 3000) {
                        valStr = valStr.substring(0, 3000) + "... [TRUNCATED]";
                    }
                    sb.append("[").append(entry.getKey()).append("]: ").append(valStr).append("\n\n");
                }
            }

            if (!foundHighlight) {
                // Fallback: return title + description even without HL tags
                sb.append("No highlighted passages found (highlighting may not be enabled on this site).\n");
                sb.append("Returning key fields instead:\n\n");
                for (String key : List.of("title", "description", "text", "abstract")) {
                    Object val = docBean.getFields().get(key);
                    if (val != null) {
                        String valStr = val.toString();
                        if (valStr.length() > 2000) {
                            valStr = valStr.substring(0, 2000) + "... [TRUNCATED]";
                        }
                        sb.append("[").append(key).append("]: ").append(valStr).append("\n\n");
                    }
                }
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("[SemanticNav Tool] get_document_highlights failed", e);
            return "Error extracting highlights. Check if the document ID and site name are correct.";
        }
    }

    @Tool(name = "compare_items", description = """
            Compares specific fields of two or more documents side-by-side in a structured table format.
            Call this when the user asks for differences, similarities, or a comparison between courses,
            articles, events, or any other content items.
            Args:
                siteName (str): Required. Name of the site.
                locale (str): Required. Use 'pt_BR' or 'en'.
                documentIds (str): Required. Comma-separated document IDs (2 to 5 IDs).
                fieldsToCompare (str): Required. Comma-separated field names to compare
                    (e.g., 'title,description,author,date,url').""")
    public String compareItems(String siteName, String locale,
            String documentIds, String fieldsToCompare) {
        log.info("[SemanticNav Tool] compare_items called: siteName={}, locale={}, ids={}, fields={}",
                siteName, locale, documentIds, fieldsToCompare);
        try {
            String[] ids = documentIds.split(",");
            String[] fieldNames = fieldsToCompare.split(",");

            if (ids.length < 2 || ids.length > 5) {
                return "Please provide between 2 and 5 document IDs for comparison.";
            }

            HttpTurSNServer turSNServer = new HttpTurSNServer(
                    URI.create("http://localhost:" + serverPort),
                    siteName, LocaleUtils.toLocale(locale));

            // Fetch each document
            Map<String, Map<String, String>> documentsData = new LinkedHashMap<>();
            for (String id : ids) {
                String trimmedId = id.trim();
                TurSNQuery turSNQuery = new TurSNQuery();
                turSNQuery.setQuery("id:\"" + trimmedId + "\"");
                turSNQuery.setRows(1);
                turSNQuery.setPageNumber(1);

                QueryTurSNResponse response = turSNServer.query(turSNQuery);
                TurSNDocumentList results = response.getResults();

                Map<String, String> fieldValues = new LinkedHashMap<>();
                if (results != null && results.getTurSNDocuments() != null
                        && !results.getTurSNDocuments().isEmpty()) {
                    var doc = results.getTurSNDocuments().getFirst();
                    if (doc.getContent() != null && doc.getContent().getFields() != null) {
                        Map<String, Object> fields = doc.getContent().getFields();
                        for (String fieldName : fieldNames) {
                            String fn = fieldName.trim();
                            Object val = fields.get(fn);
                            String valStr = val != null ? val.toString() : "(not available)";
                            if (valStr.length() > 500) {
                                valStr = valStr.substring(0, 500) + "...";
                            }
                            fieldValues.put(fn, valStr);
                        }
                    }
                } else {
                    for (String fieldName : fieldNames) {
                        fieldValues.put(fieldName.trim(), "(document not found)");
                    }
                }
                documentsData.put(trimmedId, fieldValues);
            }

            // Build side-by-side comparison
            StringBuilder sb = new StringBuilder();
            sb.append("=== Document Comparison ===\n\n");

            List<String> docIds = documentsData.keySet().stream().toList();

            // Header: get titles for column labels
            sb.append("| Field |");
            for (int i = 0; i < docIds.size(); i++) {
                String docId = docIds.get(i);
                Map<String, String> fields = documentsData.get(docId);
                String title = fields.getOrDefault("title", "Doc " + (i + 1));
                if (title.length() > 40) {
                    title = title.substring(0, 40) + "...";
                }
                sb.append(" ").append(title).append(" |");
            }
            sb.append("\n");

            // Separator
            sb.append("|-------|");
            for (int i = 0; i < docIds.size(); i++) {
                sb.append("-------|");
            }
            sb.append("\n");

            // Data rows
            for (String fieldName : fieldNames) {
                String fn = fieldName.trim();
                sb.append("| **").append(fn).append("** |");
                for (String docId : docIds) {
                    String val = documentsData.get(docId).getOrDefault(fn, "(n/a)");
                    if (val.length() > 200) {
                        val = val.substring(0, 200) + "...";
                    }
                    val = val.replace("|", "/").replace("\n", " ");
                    sb.append(" ").append(val).append(" |");
                }
                sb.append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            log.error("[SemanticNav Tool] compare_items failed", e);
            return "Error comparing documents. Check if the document IDs are correct.";
        }
    }

    @Tool(name = "search_recent_updates", description = """
            Retrieves the newest or most recently updated content about a topic.
            Use this IMMEDIATELY when the user asks for 'latest', 'newest', 'recent news',
            'updates', or 'what happened recently'. This tool handles date sorting automatically,
            so the LLM does NOT need to build date filter queries.
            Args:
                siteName (str): Required. Name of the site.
                locale (str): Required. Use 'pt_BR' or 'en'.
                query (str): Required. The topic to search for. Use '*' for all recent content.
                rows (int): Optional. Number of recent results. Defaults to 5.
                filterQueries (str): Optional. Additional filters. Format: field_name:VALUE. Separate with pipe (|).""")
    public String searchRecentUpdates(String siteName, String locale, String query,
            Integer rows, String filterQueries) {
        log.info("[SemanticNav Tool] search_recent_updates called: siteName={}, locale={}, query='{}', rows={}, fq={}",
                siteName, locale, query, rows, filterQueries);
        try {
            HttpTurSNServer turSNServer = new HttpTurSNServer(
                    URI.create("http://localhost:" + serverPort),
                    siteName, LocaleUtils.toLocale(locale));

            TurSNQuery turSNQuery = new TurSNQuery();
            turSNQuery.setQuery((query != null && !query.isBlank()) ? query : "*");
            turSNQuery.setRows(rows != null && rows > 0 ? rows : 5);
            turSNQuery.setPageNumber(1);
            turSNQuery.setSortField(TurSNQuery.Order.desc); // "newest" sort

            if (filterQueries != null && !filterQueries.isBlank()) {
                String[] fqs = filterQueries.split("\\|");
                turSNQuery.addFilterQuery(fqs);
            }

            QueryTurSNResponse response = turSNServer.query(turSNQuery);
            TurSNDocumentList results = response.getResults();

            if (results == null || results.getTurSNDocuments() == null || results.getTurSNDocuments().isEmpty()) {
                return "No recent content found for query: " + query;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== Latest Content ===\n");
            sb.append("Found ").append(results.getQueryContext() != null
                    ? results.getQueryContext().getCount()
                    : results.getTurSNDocuments().size())
                    .append(" total results. Showing ").append(results.getTurSNDocuments().size())
                    .append(" most recent:\n\n");

            int idx = 0;
            for (var doc : results.getTurSNDocuments()) {
                idx++;
                sb.append("--- ").append(idx).append(" ---\n");
                if (doc.getContent() != null && doc.getContent().getFields() != null) {
                    Map<String, Object> fields = doc.getContent().getFields();
                    for (String key : List.of("id", "title", "url", "date",
                            "modification_date", "publication_date", "type", "description")) {
                        Object value = fields.get(key);
                        if (value != null) {
                            String valStr = value.toString();
                            if (valStr.length() > 300) {
                                valStr = valStr.substring(0, 300) + "...";
                            }
                            sb.append(key).append(": ").append(valStr).append("\n");
                        }
                    }
                }
                sb.append("\n");
            }

            sb.append("TIP: Use get_document_details with a document ID to read full content.");
            return sb.toString();
        } catch (Exception e) {
            log.error("[SemanticNav Tool] search_recent_updates failed", e);
            return "Error searching for recent content. Suggest to the user to try again.";
        }
    }

    @Tool(name = "get_facet_summary", description = """
            Provides a statistical summary of ALL categories and attributes available for a given search query.
            Call this tool when the user asks 'What types of X do you have?', 'How is the content organized?',
            'What categories exist?', or wants a panoramic overview of available content without reading documents.
            Unlike get_valid_filter_values (which returns values for ONE field), this returns ALL facet fields at once.
            Args:
                siteName (str): Required. Site name.
                locale (str): Required. Search locale (e.g., 'pt_BR' or 'en').
                query (str): Optional. The search term to scope the summary. Use '*' for the entire site.""")
    public String getFacetSummary(String siteName, String locale, String query) {
        log.info("[SemanticNav Tool] get_facet_summary called: siteName={}, locale={}, query='{}'",
                siteName, locale, query);
        try {
            HttpTurSNServer turSNServer = new HttpTurSNServer(
                    URI.create("http://localhost:" + serverPort),
                    siteName, LocaleUtils.toLocale(locale));

            TurSNQuery turSNQuery = new TurSNQuery();
            turSNQuery.setQuery((query != null && !query.isBlank()) ? query : "*");
            turSNQuery.setRows(1);
            turSNQuery.setPageNumber(1);

            QueryTurSNResponse response = turSNServer.query(turSNQuery);

            long totalCount = 0;
            if (response.getResults() != null && response.getResults().getQueryContext() != null) {
                totalCount = response.getResults().getQueryContext().getCount();
            }

            TurSNFacetFieldList facetFields = response.getFacetFields();
            if (facetFields == null || !facetFields.iterator().hasNext()) {
                return "No facet data available for site: " + siteName
                        + ". Total documents matching query '" + query + "': " + totalCount;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== Site Content Summary ===\n");
            sb.append("Query: '").append(query).append("'\n");
            sb.append("Total matching documents: ").append(totalCount).append("\n\n");

            for (TurSNFacetField ff : facetFields) {
                sb.append(">> ").append(ff.getName());
                if (ff.getLabel() != null) {
                    sb.append(" (").append(ff.getLabel()).append(")");
                }
                sb.append(":\n");

                List<TurSNFacetFieldValue> values = ff.getValues().getTurSNFacetFieldValues();
                if (values != null && !values.isEmpty()) {
                    for (TurSNFacetFieldValue value : values) {
                        sb.append("   - ").append(value.getLabel())
                                .append(" (").append(value.getCount()).append(")\n");
                    }
                } else {
                    sb.append("   (no values)\n");
                }
                sb.append("\n");
            }

            sb.append("TIP: Use get_valid_filter_values to see ALL values for a specific facet field.");
            String result = sb.toString();
            log.info("[SemanticNav Tool] get_facet_summary result:\n{}", result);
            return result;
        } catch (Exception e) {
            log.error("[SemanticNav Tool] get_facet_summary failed", e);
            return "Error retrieving facet summary. Check if the site name is correct.";
        }
    }

    @Tool(name = "search_by_date_range", description = """
            Searches for documents within a specific date range.
            Use this INSTEAD of search_site when the user asks for content from a specific period
            (e.g., 'news from last year', 'events in October 2023', 'publications between March and June').
            This tool handles date syntax automatically - the LLM only needs to provide simple YYYY-MM-DD dates.
            Args:
                siteName (str): Required. Site name.
                locale (str): Required. Search locale (e.g., 'pt_BR' or 'en').
                query (str): Required. The search term (or '*' for all content in the date range).
                dateFieldName (str): Required. The name of the date field to filter on
                    (must be discovered via get_site_fields, e.g., 'date', 'publication_date', 'modification_date').
                startDate (str): Required. Start date in format YYYY-MM-DD.
                endDate (str): Required. End date in format YYYY-MM-DD. Use the same date as startDate for a single day.
                rows (int): Optional. Number of results. Defaults to 10.""")
    public String searchByDateRange(String siteName, String locale, String query,
            String dateFieldName, String startDate, String endDate, Integer rows) {
        log.info(
                "[SemanticNav Tool] search_by_date_range called: siteName={}, locale={}, query='{}', field={}, start={}, end={}, rows={}",
                siteName, locale, query, dateFieldName, startDate, endDate, rows);
        try {
            HttpTurSNServer turSNServer = new HttpTurSNServer(
                    URI.create("http://localhost:" + serverPort),
                    siteName, LocaleUtils.toLocale(locale));

            TurSNQuery turSNQuery = new TurSNQuery();
            turSNQuery.setQuery((query != null && !query.isBlank()) ? query : "*");
            turSNQuery.setRows(rows != null && rows > 0 ? rows : 10);
            turSNQuery.setPageNumber(1);
            turSNQuery.setSortField(TurSNQuery.Order.desc);

            // Build safe date filter query: fieldName:[startDateT00:00:00Z TO
            // endDateT23:59:59Z]
            String dateFilter = dateFieldName + ":[" + startDate + "T00:00:00Z TO " + endDate + "T23:59:59Z]";
            turSNQuery.addFilterQuery(dateFilter);

            QueryTurSNResponse response = turSNServer.query(turSNQuery);
            TurSNDocumentList results = response.getResults();

            if (results == null || results.getTurSNDocuments() == null || results.getTurSNDocuments().isEmpty()) {
                return "No results found for query '" + query + "' between " + startDate + " and " + endDate
                        + ". Try expanding the date range or changing the search terms.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("=== Date Range Search ===\n");
            sb.append("Query: '").append(query).append("'\n");
            sb.append("Period: ").append(startDate).append(" to ").append(endDate).append("\n");
            sb.append("Found ").append(results.getQueryContext() != null
                    ? results.getQueryContext().getCount()
                    : results.getTurSNDocuments().size())
                    .append(" results (showing ").append(results.getTurSNDocuments().size()).append("):\n\n");

            int idx = 0;
            for (var doc : results.getTurSNDocuments()) {
                idx++;
                sb.append("--- ").append(idx).append(" ---\n");
                if (doc.getContent() != null && doc.getContent().getFields() != null) {
                    Map<String, Object> fields = doc.getContent().getFields();
                    for (String key : List.of("id", "title", "url", dateFieldName, "type", "description")) {
                        Object value = fields.get(key);
                        if (value != null) {
                            String valStr = value.toString();
                            if (valStr.length() > 300) {
                                valStr = valStr.substring(0, 300) + "...";
                            }
                            sb.append(key).append(": ").append(valStr).append("\n");
                        }
                    }
                }
                sb.append("\n");
            }

            sb.append("TIP: Use get_document_details with a document ID to read full content.");
            return sb.toString();
        } catch (Exception e) {
            log.error("[SemanticNav Tool] search_by_date_range failed", e);
            return "Error executing date range search. Verify the date format (YYYY-MM-DD) and field name.";
        }
    }

    @Tool(name = "lookup_facet_value", description = """
            Looks up a SINGLE term across all available facets to find exact filter values in the database.
            Uses word-stem matching so inflected forms (plurals, conjugations) also match.
            CRITICAL: Call this BEFORE using 'filterQueries' in 'search_site' when you know a single keyword
            but not the exact database value (e.g., 'docente', 'inovacao', 'silva').
            For multi-word user phrases, prefer 'discover_facet_values' instead.
            Args:
                siteName (str): Required. The site name.
                locale (str): Required. The locale (e.g., 'pt_BR' or 'en').
                term (str): Required. A single word or short term to look for inside facet values.""")
    public String lookupFacetValue(String siteName, String locale, String term) {
        log.info("[SemanticNav Tool] lookup_facet_value called: siteName={}, locale={}, term='{}'",
                siteName, locale, term);
        try {
            TurSNFacetFieldList facetFields = fetchFacetFields(siteName, locale);
            if (facetFields == null) {
                return "No facets found for site: " + siteName;
            }
            return matchTermAgainstFacets(term, facetFields);
        } catch (Exception e) {
            log.error("[SemanticNav Tool] lookup_facet_value failed", e);
            return "Error retrieving facet values. Check if the site name is correct.";
        }
    }

    @Tool(name = "discover_facet_values", description = """
            Splits a user phrase into individual words and searches ALL facets for values matching each word.
            Uses word-stem matching for better recall (e.g., 'docentes' matches 'Docente').
            CRITICAL USE CASE: When the user asks to filter by a phrase like 'cursos de engenharia' or
            'professores de matemática', call this tool to discover which facet fields and values exist
            for EACH word in the phrase. This prevents hallucinating filter values.
            For a single keyword, you may use 'lookup_facet_value' instead.
            Args:
                siteName (str): Required. The site name.
                locale (str): Required. The locale (e.g., 'pt_BR' or 'en').
                text (str): Required. A phrase or sentence whose words will be searched individually
                    (e.g., 'cursos engenharia computação', 'professores matemática').""")
    public String discoverFacetValues(String siteName, String locale, String text) {
        log.info("[SemanticNav Tool] discover_facet_values called: siteName={}, locale={}, text='{}'",
                siteName, locale, text);
        try {
            TurSNFacetFieldList facetFields = fetchFacetFields(siteName, locale);
            if (facetFields == null) {
                return "No facets found for site: " + siteName;
            }

            String[] words = text.trim().toLowerCase().split("\\s+");
            Set<String> stopWords = Set.of(
                    "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas",
                    "o", "a", "os", "as", "um", "uma", "uns", "umas",
                    "e", "ou", "com", "por", "para", "que", "se",
                    "the", "of", "in", "and", "or", "for", "to", "an", "is", "on", "at", "by", "with");

            List<String> meaningfulWords = java.util.Arrays.stream(words)
                    .filter(w -> w.length() > 2 && !stopWords.contains(w))
                    .distinct()
                    .toList();

            if (meaningfulWords.isEmpty()) {
                return "No meaningful words found in: '" + text + "'. Provide more specific terms.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Facet discovery for phrase: '").append(text).append("'\n");
            sb.append("Words analyzed: ").append(String.join(", ", meaningfulWords)).append("\n\n");

            boolean foundAny = false;
            for (String word : meaningfulWords) {
                String wordResult = matchTermAgainstFacets(word, facetFields);
                if (!wordResult.startsWith("No facet values")) {
                    sb.append("--- Results for '").append(word).append("' ---\n");
                    sb.append(wordResult).append("\n");
                    foundAny = true;
                }
            }

            if (!foundAny) {
                return "No facet values found for any word in '" + text
                        + "'. Do NOT use these terms as filters. Put them in the main 'query' parameter instead.";
            }

            String result = sb.toString();
            log.info("[SemanticNav Tool] discover_facet_values result:\n{}", result);
            return result;
        } catch (Exception e) {
            log.error("[SemanticNav Tool] discover_facet_values failed", e);
            return "Error retrieving facet values. Check if the site name is correct.";
        }
    }

    private TurSNFacetFieldList fetchFacetFields(String siteName, String locale) {
        HttpTurSNServer turSNServer = new HttpTurSNServer(
                URI.create("http://localhost:" + serverPort),
                siteName, LocaleUtils.toLocale(locale));

        TurSNQuery turSNQuery = new TurSNQuery();
        turSNQuery.setQuery("*");
        turSNQuery.setRows(1);
        turSNQuery.setPageNumber(1);

        QueryTurSNResponse response = turSNServer.query(turSNQuery);
        TurSNFacetFieldList facetFields = response.getFacetFields();
        if (facetFields == null || !facetFields.iterator().hasNext()) {
            return null;
        }
        return facetFields;
    }

    private String matchTermAgainstFacets(String term, TurSNFacetFieldList facetFields) {
        String searchTermLower = term.toLowerCase();
        String stemmedTerm = stemWord(searchTermLower);
        StringBuilder sb = new StringBuilder();
        sb.append("Facet values matching '").append(term).append("' (stem: '")
                .append(stemmedTerm).append("'):\n\n");

        boolean foundAny = false;

        for (TurSNFacetField facet : facetFields) {
            List<TurSNFacetFieldValue> values = facet.getValues().getTurSNFacetFieldValues();
            if (values == null || values.isEmpty()) {
                continue;
            }

            String matchedValues = values.stream()
                    .filter(v -> v.getLabel() != null && matchesByStem(v.getLabel(), stemmedTerm))
                    .map(v -> "'" + v.getLabel() + "' (" + v.getCount() + " items)")
                    .collect(Collectors.joining(", "));

            if (!matchedValues.isEmpty()) {
                sb.append("Field: '").append(facet.getName()).append("'\n");
                sb.append("  Valid Values: ").append(matchedValues).append("\n\n");
                foundAny = true;
            }
        }

        if (!foundAny) {
            return "No facet values found matching '" + term + "' (stem: '" + stemmedTerm
                    + "'). Do NOT use this term as a filter. Put it in the main 'query' parameter instead.";
        }

        sb.append("INSTRUCTION: Use the Field name and one of the Valid Values (inside quotes) ")
                .append("to build your 'filterQueries' in 'search_site'. ")
                .append("Format: field_name:exact_value");
        return sb.toString();
    }

    private boolean matchesByStem(String label, String stemmedTerm) {
        String labelLower = label.toLowerCase();
        if (labelLower.contains(stemmedTerm)) {
            return true;
        }
        for (String word : labelLower.split("[\\s,;/|&()\\[\\]{}\"']+")) {
            if (!word.isEmpty() && stemWord(word).contains(stemmedTerm)) {
                return true;
            }
        }
        return false;
    }

    private record StemRule(String suffix, int minLength, String replacement) {
        boolean matches(String word) {
            return word.length() >= minLength && word.endsWith(suffix);
        }

        String apply(String word) {
            return word.substring(0, word.length() - suffix.length()) + replacement;
        }
    }

    private static final List<StemRule> STEM_RULES = List.of(
            new StemRule("ções", 5, "ção"),
            new StemRule("ões", 4, "ão"),
            new StemRule("ães", 4, "ão"),
            new StemRule("ais", 4, "l"),
            new StemRule("éis", 4, "el"),
            new StemRule("óis", 4, "ol"),
            new StemRule("eis", 4, "l"),
            new StemRule("is", 5, "i"),
            new StemRule("res", 6, "r"),
            new StemRule("es", 5, "e"),
            new StemRule("ns", 4, "m"),
            new StemRule("s", 4, "")
    );

    private String stemWord(String word) {
        if (word == null || word.length() <= 3) {
            return word;
        }
        String s = word.toLowerCase();
        return STEM_RULES.stream()
                .filter(rule -> rule.matches(s))
                .findFirst()
                .map(rule -> rule.apply(s))
                .orElse(s);
    }
}
