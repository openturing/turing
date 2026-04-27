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

    private static final String ENABLED = "enabled";
    private static final String DISABLED = "disabled";

    private final TurSNSiteLocaleRepository snSiteLocaleRepository;
    private final TurSNSiteMetricAccessRepository metricAccessRepository;
    private final TurSearchEnginePluginFactory pluginFactory;

    public String collectSiteData(TurSNSite site) {
        StringBuilder sb = new StringBuilder();
        appendSiteHeader(sb, site);
        appendLocalesSection(sb, site);
        appendDocumentCountsSection(sb, site);
        appendFieldsSection(sb, site);
        appendSearchMetricsSection(sb, site);
        appendGenAiConfigurationSection(sb, site);
        return sb.toString();
    }

    private void appendSiteHeader(StringBuilder sb, TurSNSite site) {
        sb.append("# Site: ").append(site.getName()).append("\n");
        if (StringUtils.hasText(site.getDescription())) {
            sb.append("Description: ").append(site.getDescription()).append("\n");
        }
        sb.append("Search Engine: ").append(site.getTurSEInstance() != null
                ? site.getTurSEInstance().getTitle()
                : "Not configured").append("\n");
        sb.append("Rows per page: ").append(site.getRowsPerPage()).append("\n");
        sb.append("Highlight: ").append(isEnabled(site.getHl()) ? ENABLED : DISABLED).append("\n");
        sb.append("Spell check: ").append(isEnabled(site.getSpellCheck()) ? ENABLED : DISABLED).append("\n");
        sb.append("More like this: ").append(isEnabled(site.getMlt()) ? ENABLED : DISABLED).append("\n");
        sb.append("Facets: ").append(isEnabled(site.getFacet()) ? ENABLED : DISABLED);
        if (isEnabled(site.getFacet())) {
            sb.append(" (items per facet: ").append(site.getItemsPerFacet()).append(")");
        }
        sb.append("\n");
        sb.append("Wildcard on no results: ").append(isEnabled(site.getWildcardNoResults()) ? "yes" : "no").append("\n");
        sb.append("Exact match: ").append(isEnabled(site.getExactMatch()) ? ENABLED : DISABLED).append("\n\n");
    }

    private void appendLocalesSection(StringBuilder sb, TurSNSite site) {
        var locales = snSiteLocaleRepository.findByTurSNSite(site);
        sb.append("## Locales (").append(locales.size()).append(")\n");
        for (var locale : locales) {
            sb.append("- ").append(locale.getLanguage()).append(" (core: ").append(locale.getCore()).append(")\n");
        }
        sb.append("\n");
    }

    private void appendDocumentCountsSection(StringBuilder sb, TurSNSite site) {
        var locales = snSiteLocaleRepository.findByTurSNSite(site);
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
    }

    private void appendFieldsSection(StringBuilder sb, TurSNSite site) {
        var fields = site.getTurSNSiteFieldExts();
        if (fields == null || fields.isEmpty()) {
            return;
        }
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

    private void appendSearchMetricsSection(StringBuilder sb, TurSNSite site) {
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
                sb.append("  - \"").append(term.getTerm()).append("\" (").append(term.getTotal())
                        .append(" searches)\n");
            }
        }
        sb.append("\n");

        Instant monthAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        int searchesThisMonth = metricAccessRepository.countTermsByPeriod(site, monthAgo, now);
        sb.append("## Search Metrics (Last 30 days)\n");
        sb.append("Total searches: ").append(searchesThisMonth).append("\n\n");
    }

    private void appendGenAiConfigurationSection(StringBuilder sb, TurSNSite site) {
        var genAi = site.getTurSNSiteGenAi();
        var agent = genAi == null ? null : genAi.getTurAIAgent();
        sb.append("## Generative AI Configuration\n");
        if (agent != null && agent.getEnabled() == 1 && agent.isRagEnabled()) {
            sb.append("- Enabled: yes\n");
            sb.append("- AI Agent: ").append(agent.getTitle()).append("\n");
            if (agent.getLlmInstances() != null && !agent.getLlmInstances().isEmpty()) {
                sb.append("- LLM: ")
                        .append(agent.getLlmInstances().iterator().next().getTitle())
                        .append("\n");
            }
        } else {
            sb.append("- Enabled: no\n");
        }
    }

    private static boolean isEnabled(Integer value) {
        return Integer.valueOf(1).equals(value);
    }
}
