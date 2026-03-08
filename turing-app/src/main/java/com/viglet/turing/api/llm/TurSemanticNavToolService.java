package com.viglet.turing.api.llm;

import java.net.URI;
import java.nio.charset.StandardCharsets;
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
            2. If the user asks for a general topic (e.g., "Insper", "Machine Learning"), put it in 'query'!
            3. ONLY use filterQueries with EXACT values confirmed by get_valid_filter_values.

            Args:
                siteName (str): Required. Use 'insper-stage-publish' as default if unknown.
                locale (str): Required. Use 'pt_BR' or 'en'.
                query (str): Required. The main search text. Put all general keywords here.
                rows (int): Optional. Number of results. Defaults to 10.
                page (int): Optional. Defaults to 1.
                filterQueries (str): Optional. Format: field_name:VALUE. Separate with pipe (|).
            """)
    public String searchSite(String siteName, String locale, String query,
            int rows, int page, String filterQueries) {

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

            turSNQuery.setRows(rows > 0 ? rows : 10);
            turSNQuery.setPageNumber(page > 0 ? page : 1);
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
                if (similar.getId() != null) sb.append("id: ").append(similar.getId()).append("\n");
                if (similar.getTitle() != null) sb.append("title: ").append(similar.getTitle()).append("\n");
                if (similar.getType() != null) sb.append("type: ").append(similar.getType()).append("\n");
                if (similar.getUrl() != null) sb.append("url: ").append(similar.getUrl()).append("\n");
                sb.append("\n");
            }

            sb.append("TIP: Use get_document_details with a document ID to read full content.");
            return sb.toString();
        } catch (Exception e) {
            log.error("[SemanticNav Tool] find_similar_documents failed", e);
            return "Error finding similar documents. Check if the document ID is correct.";
        }
    }

}
