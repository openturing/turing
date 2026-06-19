package com.viglet.turing.sn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

/**
 * Tests for TurSNSiteDataCollectorService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TurSNSiteDataCollectorServiceTest {

    @Mock
    private TurSNSiteLocaleRepository snSiteLocaleRepository;

    @Mock
    private TurSNSiteMetricAccessRepository metricAccessRepository;

    @Mock
    private TurSearchEnginePluginFactory pluginFactory;

    @Mock
    private TurSearchEnginePlugin searchEnginePlugin;

    private TurSNSiteDataCollectorService service;

    @BeforeEach
    void setUp() {
        service = new TurSNSiteDataCollectorService(
                snSiteLocaleRepository,
                metricAccessRepository,
                pluginFactory);
    }

    private TurSNSite createSite(String name) {
        TurSNSite site = new TurSNSite();
        site.setId("site-1");
        site.setName(name);
        return site;
    }

    private TurSNSiteLocale createLocale(TurSNSite site, String lang, String core) {
        TurSNSiteLocale locale = new TurSNSiteLocale();
        locale.setLanguage(java.util.Locale.forLanguageTag(lang));
        locale.setCore(core);
        locale.setTurSNSite(site);
        return locale;
    }

    @Test
    void collectSiteDataContainsSiteHeader() {
        TurSNSite site = createSite("MySite");
        site.setRowsPerPage(20);
        site.setHl(1);
        site.setSpellCheck(1);
        site.setMlt(0);
        site.setFacet(1);
        site.setItemsPerFacet(10);
        site.setWildcardNoResults(0);
        site.setExactMatch(1);

        TurSEInstance seInstance = new TurSEInstance();
        seInstance.setTitle("Solr Engine");
        site.setTurSEInstance(seInstance);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result)
                .contains("# Site: MySite")
                .contains("Search Engine: Solr Engine")
                .contains("Rows per page: 20")
                .contains("Highlight: enabled")
                .contains("Spell check: enabled")
                .contains("More like this: disabled")
                .contains("Facets: enabled (items per facet: 10)")
                .contains("Wildcard on no results: no")
                .contains("Exact match: enabled");
    }

    @Test
    void collectSiteDataWithNullSEInstance() {
        TurSNSite site = createSite("NoEngine");
        site.setTurSEInstance(null);
        site.setRowsPerPage(10);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result).contains("Search Engine: Not configured");
    }

    @Test
    void collectSiteDataWithDescription() {
        TurSNSite site = createSite("DescSite");
        site.setDescription("A test description");
        site.setRowsPerPage(5);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result).contains("Description: A test description");
    }

    @Test
    void collectSiteDataWithoutDescription() {
        TurSNSite site = createSite("NoDescSite");
        site.setDescription(null);
        site.setRowsPerPage(10);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result).doesNotContain("Description:");
    }

    @Test
    void collectSiteDataWithEmptyDescription() {
        TurSNSite site = createSite("EmptyDescSite");
        site.setDescription("");
        site.setRowsPerPage(10);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result).doesNotContain("Description:");
    }

    @Test
    void collectSiteDataLocalesSection() {
        TurSNSite site = createSite("LocaleSite");
        site.setRowsPerPage(10);

        TurSNSiteLocale enLocale = createLocale(site, "en", "en_core");
        TurSNSiteLocale ptLocale = createLocale(site, "pt", "pt_core");

        when(snSiteLocaleRepository.findByTurSNSite(site))
                .thenReturn(List.of(enLocale, ptLocale));
        when(pluginFactory.getPluginForSite(site)).thenReturn(searchEnginePlugin);
        when(searchEnginePlugin.getDocumentTotal(any())).thenReturn(100L);
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result)
                .contains("## Locales (2)")
                .contains("(core: en_core)")
                .contains("(core: pt_core)");
    }

    @Test
    void collectSiteDataDocumentCountsSection() {
        TurSNSite site = createSite("DocCountSite");
        site.setRowsPerPage(10);

        TurSNSiteLocale enLocale = createLocale(site, "en", "en_core");

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(enLocale));
        when(pluginFactory.getPluginForSite(site)).thenReturn(searchEnginePlugin);
        when(searchEnginePlugin.getDocumentTotal(enLocale)).thenReturn(500L);
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result)
                .contains("## Document Counts")
                .contains("500 documents");
    }

    @Test
    void collectSiteDataDocumentCountsUnavailableOnException() {
        TurSNSite site = createSite("ErrorSite");
        site.setRowsPerPage(10);

        TurSNSiteLocale enLocale = createLocale(site, "en", "en_core");

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(enLocale));
        when(pluginFactory.getPluginForSite(site)).thenThrow(new RuntimeException("Connection failed"));
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result).contains("unavailable");
    }

    @Test
    void collectSiteDataFieldsSection() {
        TurSNSite site = createSite("FieldsSite");
        site.setRowsPerPage(10);

        TurSNSiteFieldExt enabledField = new TurSNSiteFieldExt();
        enabledField.setName("title");
        enabledField.setEnabled(1);
        enabledField.setFacet(1);
        enabledField.setRequired(1);

        TurSNSiteFieldExt disabledField = new TurSNSiteFieldExt();
        disabledField.setName("hidden");
        disabledField.setEnabled(0);
        disabledField.setFacet(0);
        disabledField.setRequired(0);

        Set<TurSNSiteFieldExt> fields = new HashSet<>();
        fields.add(enabledField);
        fields.add(disabledField);
        site.setTurSNSiteFieldExts(fields);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result)
                .contains("## Fields (2)")
                .contains("- Enabled: 1")
                .contains("- Faceted: 1")
                .contains("- Required: 1");
    }

    @Test
    void collectSiteDataFieldsSectionSkippedWhenNull() {
        TurSNSite site = createSite("NoFieldsSite");
        site.setRowsPerPage(10);
        site.setTurSNSiteFieldExts(null);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result).doesNotContain("## Fields");
    }

    @Test
    void collectSiteDataFieldsSectionSkippedWhenEmpty() {
        TurSNSite site = createSite("EmptyFieldsSite");
        site.setRowsPerPage(10);
        site.setTurSNSiteFieldExts(new HashSet<>());

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result).doesNotContain("## Fields");
    }

    @Test
    void collectSiteDataSearchMetricsSection() {
        TurSNSite site = createSite("MetricsSite");
        site.setRowsPerPage(10);

        TurSNSiteMetricAccessTerm term1 = new TurSNSiteMetricAccessTerm("java", 50, 120.0);
        TurSNSiteMetricAccessTerm term2 = new TurSNSiteMetricAccessTerm("spring", 30, 80.0);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any()))
                .thenReturn(200)
                .thenReturn(500);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(List.of(term1, term2));

        String result = service.collectSiteData(site);

        assertThat(result)
                .contains("## Search Metrics (Last 7 days)")
                .contains("Total searches: 200")
                .contains("\"java\" (50 searches)")
                .contains("\"spring\" (30 searches)")
                .contains("## Search Metrics (Last 30 days)");
    }

    @Test
    void collectSiteDataSearchMetricsWithNoTopTerms() {
        TurSNSite site = createSite("NoTermsSite");
        site.setRowsPerPage(10);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result)
                .contains("Total searches: 0")
                .doesNotContain("Top search terms:");
    }

    @Test
    void collectSiteDataGenAiEnabledWithLLM() {
        TurSNSite site = createSite("GenAiSite");
        site.setRowsPerPage(10);

        com.viglet.turing.persistence.model.agent.TurAIAgent agent =
                new com.viglet.turing.persistence.model.agent.TurAIAgent();
        agent.setEnabled(1);
        agent.setRagEnabled(true);
        TurLLMInstance llmInstance = new TurLLMInstance();
        llmInstance.setTitle("GPT-4");
        agent.getLlmInstances().add(llmInstance);

        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setTurAIAgent(agent);
        site.setTurSNSiteGenAi(genAi);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result)
                .contains("## Generative AI Configuration")
                .contains("- Enabled: yes")
                .contains("- LLM: GPT-4");
    }

    @Test
    void collectSiteDataGenAiEnabledWithoutLLM() {
        TurSNSite site = createSite("GenAiNoLLM");
        site.setRowsPerPage(10);

        com.viglet.turing.persistence.model.agent.TurAIAgent agent =
                new com.viglet.turing.persistence.model.agent.TurAIAgent();
        agent.setEnabled(1);
        agent.setRagEnabled(true);
        // No LLM attached to the agent.

        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        genAi.setTurAIAgent(agent);
        site.setTurSNSiteGenAi(genAi);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result)
                .contains("- Enabled: yes")
                .doesNotContain("- LLM:");
    }

    @Test
    void collectSiteDataGenAiDisabled() {
        TurSNSite site = createSite("GenAiOff");
        site.setRowsPerPage(10);

        TurSNSiteGenAi genAi = new TurSNSiteGenAi();
        // No agent attached → GenAI disabled.
        site.setTurSNSiteGenAi(genAi);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result).contains("- Enabled: no");
    }

    @Test
    void collectSiteDataGenAiNull() {
        TurSNSite site = createSite("NoGenAi");
        site.setRowsPerPage(10);
        site.setTurSNSiteGenAi(null);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result).contains("- Enabled: no");
    }

    @Test
    void collectSiteDataAllFeaturesDisabled() {
        TurSNSite site = createSite("AllOff");
        site.setRowsPerPage(10);
        site.setHl(0);
        site.setSpellCheck(0);
        site.setMlt(0);
        site.setFacet(0);
        site.setWildcardNoResults(0);
        site.setExactMatch(0);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result)
                .contains("Highlight: disabled")
                .contains("Spell check: disabled")
                .contains("More like this: disabled")
                .contains("Facets: disabled")
                .doesNotContain("items per facet")
                .contains("Wildcard on no results: no")
                .contains("Exact match: disabled");
    }

    @Test
    void collectSiteDataNullIntegerFeaturesAreTreatedAsDisabled() {
        TurSNSite site = createSite("NullFeatures");
        site.setRowsPerPage(10);
        site.setHl(null);
        site.setSpellCheck(null);
        site.setMlt(null);
        site.setFacet(null);
        site.setWildcardNoResults(null);
        site.setExactMatch(null);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result)
                .contains("Highlight: disabled")
                .contains("Spell check: disabled")
                .contains("Facets: disabled");
    }

    @Test
    void collectSiteDataWildcardEnabled() {
        TurSNSite site = createSite("WildcardSite");
        site.setRowsPerPage(10);
        site.setWildcardNoResults(1);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result).contains("Wildcard on no results: yes");
    }

    @Test
    void collectSiteDataMultipleLocalesWithDocumentCounts() {
        TurSNSite site = createSite("MultiLocale");
        site.setRowsPerPage(10);

        TurSNSiteLocale enLocale = createLocale(site, "en", "en_core");
        TurSNSiteLocale ptLocale = createLocale(site, "pt-BR", "pt_core");
        TurSNSiteLocale esLocale = createLocale(site, "es", "es_core");

        when(snSiteLocaleRepository.findByTurSNSite(site))
                .thenReturn(List.of(enLocale, ptLocale, esLocale));
        when(pluginFactory.getPluginForSite(site)).thenReturn(searchEnginePlugin);
        when(searchEnginePlugin.getDocumentTotal(enLocale)).thenReturn(1000L);
        when(searchEnginePlugin.getDocumentTotal(ptLocale)).thenReturn(500L);
        when(searchEnginePlugin.getDocumentTotal(esLocale)).thenReturn(250L);
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result)
                .contains("## Locales (3)")
                .contains("1000 documents")
                .contains("500 documents")
                .contains("250 documents");
    }

    @Test
    void collectSiteDataResultIsNotNull() {
        TurSNSite site = createSite("NonNull");
        site.setRowsPerPage(10);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    void collectSiteDataFacetsDisabledDoesNotShowItemsPerFacet() {
        TurSNSite site = createSite("FacetDisabled");
        site.setRowsPerPage(10);
        site.setFacet(0);
        site.setItemsPerFacet(15);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result)
                .contains("Facets: disabled")
                .doesNotContain("items per facet: 15");
    }

    @Test
    void collectSiteDataFacetsEnabledShowsItemsPerFacet() {
        TurSNSite site = createSite("FacetEnabled");
        site.setRowsPerPage(10);
        site.setFacet(1);
        site.setItemsPerFacet(25);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result).contains("Facets: enabled (items per facet: 25)");
    }

    @Test
    void collectSiteDataWithValueTwoForHlIsTreatedAsDisabled() {
        TurSNSite site = createSite("HlTwo");
        site.setRowsPerPage(10);
        site.setHl(2);

        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String result = service.collectSiteData(site);

        assertThat(result).contains("Highlight: disabled");
    }
}
