package com.viglet.turing.api.llm;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.LocaleUtils;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.viglet.turing.client.sn.HttpTurSNServer;
import com.viglet.turing.client.sn.TurSNDocumentList;
import com.viglet.turing.client.sn.TurSNQuery;
import com.viglet.turing.client.sn.response.QueryTurSNResponse;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurSemanticNavToolService {

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

    @Tool(name = "search_site", description = """
            Searches for content on a site.
            ACTION REQUIRED: If the user asks to search, call this tool IMMEDIATELY using their search terms in the 'query' parameter.

            CRITICAL RULES FOR FILTERING (filterQueries):
            1. DO NOT invent or guess values for filters.
            2. If the user asks for a general topic (e.g., "Insper", "Machine Learning"), put it in the 'query' parameter! DO NOT use it as a filter.
            3. ONLY use filterQueries if you are 100% absolutely sure of the EXACT string value registered in the database (e.g., from a previous search result).

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

            // Melhoria: Usar pipe (|) em vez de vírgula para evitar quebra em datas ou
            // textos com vírgula
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
                return "No results found for query: " + query + ". Try modifying your search terms or filters.";
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
                    // Sugestão de Melhoria (Token Management):
                    // Limitar o tamanho dos campos longos (ex: RichText, Text) para não estourar a
                    // memória do LLM.
                    doc.getContent().getFields().forEach((key, value) -> {
                        String valStr = value != null ? value.toString() : "";
                        if (valStr.length() > 500) {
                            valStr = valStr.substring(0, 500) + "... [TRUNCATED]";
                        }
                        // Opcional: Filtrar apenas campos úteis se a lista for muito grande
                        // if(List.of("title", "url", "description",
                        // "date").contains(key.toLowerCase())) { ... }
                        sb.append(key).append(": ").append(valStr).append("\n");
                    });
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("[SemanticNav Tool] search_site failed", e);
            return "Error executing search. Suggest to the user to try again.";
        }
    }
}