package com.viglet.turing.sn;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class TurSNSiteDataCollectorService {

    private final TurSNSiteLocaleRepository snSiteLocaleRepository;
    private final TurSNSiteMetricAccessRepository metricAccessRepository;
    private final TurSearchEnginePluginFactory pluginFactory;

    public String collectSiteData(TurSNSite site) {
        StringBuilder sb = new StringBuilder();

        sb.append("# Site: ").append(site.getName()).append("\n");
        if (StringUtils.hasText(site.getDescription())) {
            sb.append("Description: ").append(site.getDescription()).append("\n");
        }
        sb.append("Search Engine: ").append(site.getTurSEInstance() != null
                ? site.getTurSEInstance().getTitle() : "Not configured").append("\n");
        sb.append("Rows per page: ").append(site.getRowsPerPage()).append("\n");
        sb.append("Highlight: ").append(site.getHl() == 1 ? "enabled" : "disabled").append("\n");
        sb.append("Spell check: ").append(site.getSpellCheck() == 1 ? "enabled" : "disabled").append("\n");
        sb.append("More like this: ").append(site.getMlt() == 1 ? "enabled" : "disabled").append("\n");
        sb.append("Facets: ").append(site.getFacet() == 1 ? "enabled" : "disabled");
        if (site.getFacet() == 1) {
            sb.append(" (items per facet: ").append(site.getItemsPerFacet()).append(")");
        }
        sb.append("\n");
        sb.append("Wildcard on no results: ").append(site.getWildcardNoResults() == 1 ? "yes" : "no").append("\n");
        sb.append("Exact match: ").append(site.getExactMatch() == 1 ? "enabled" : "disabled").append("\n\n");

        var locales = snSiteLocaleRepository.findByTurSNSite(site);
        sb.append("## Locales (").append(locales.size()).append(")\n");
        for (var locale : locales) {
            sb.append("- ").append(locale.getLanguage()).append(" (core: ").append(locale.getCore()).append(")\n");
        }
        sb.append("\n");

        sb.append("## Document Counts\n");
        for (var locale : locales) {
            try {
                var plugin = pluginFactory.getPluginForSite(site);
                long count = plugin.getDocumentTotal(locale);
                sb.append("- ").append(locale.getLanguage()).append(": ").append(count).append(" documents\n");
            } catch (Exception e) {
                sb.append("- ").append(locale.getLanguage()).append(": unavailable\n");
            }
        }
        sb.append("\n");

        var fields = site.getTurSNSiteFieldExts();
        if (fields != null && !fields.isEmpty()) {
            sb.append("## Fields (").append(fields.size()).append(")\n");
            int enabledCount = (int) fields.stream().filter(f -> f.getEnabled() == 1).count();
            int facetCount = (int) fields.stream().filter(f -> f.getFacet() == 1).count();
            int requiredCount = (int) fields.stream().filter(f -> f.getRequired() == 1).count();
            sb.append("- Enabled: ").append(enabledCount).append("\n");
            sb.append("- Faceted: ").append(facetCount).append("\n");
            sb.append("- Required: ").append(requiredCount).append("\n");
            sb.append("- Field names: ");
            fields.stream()
                    .filter(f -> f.getEnabled() == 1)
                    .limit(20)
                    .forEach(f -> sb.append(f.getName()).append(", "));
            sb.append("\n\n");
        }

        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        Instant now = Instant.now();
        int searchesThisWeek = metricAccessRepository.countTermsByPeriod(site, weekAgo, now);
        sb.append("## Search Metrics (Last 7 days)\n");
        sb.append("Total searches: ").append(searchesThisWeek).append("\n");

        List<TurSNSiteMetricAccessTerm> topTerms = metricAccessRepository
                .topTermsBetweenDates(site, weekAgo, now, PageRequest.of(0, 15));
        if (!topTerms.isEmpty()) {
            sb.append("Top search terms:\n");
            for (TurSNSiteMetricAccessTerm term : topTerms) {
                sb.append("  - \"").append(term.getTerm()).append("\" (").append(term.getTotal()).append(" searches)\n");
            }
        }
        sb.append("\n");

        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        int searchesThisMonth = metricAccessRepository.countTermsByPeriod(site, monthAgo, now);
        sb.append("## Search Metrics (Last 30 days)\n");
        sb.append("Total searches: ").append(searchesThisMonth).append("\n\n");

        var genAi = site.getTurSNSiteGenAi();
        sb.append("## Generative AI Configuration\n");
        if (genAi != null && genAi.isEnabled()) {
            sb.append("- Enabled: yes\n");
            if (genAi.getTurLLMInstance() != null) {
                sb.append("- LLM: ").append(genAi.getTurLLMInstance().getTitle()).append("\n");
            }
        } else {
            sb.append("- Enabled: no\n");
        }

        return sb.toString();
    }
}
