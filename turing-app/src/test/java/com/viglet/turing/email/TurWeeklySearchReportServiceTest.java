package com.viglet.turing.email;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.viglet.turing.genai.provider.llm.TurGenAiLlmProviderFactory;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.sn.TurSNSiteDataCollectorService;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.TurLlmCacheService;
import com.viglet.turing.system.security.TurSecretCryptoService;

/**
 * Tests for TurWeeklySearchReportService.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@ExtendWith(MockitoExtension.class)
class TurWeeklySearchReportServiceTest {

    @Mock
    private TurEmailService emailService;
    @Mock
    private TurSNSiteRepository snSiteRepository;
    @Mock
    private TurSNSiteLocaleRepository snSiteLocaleRepository;
    @Mock
    private TurSNSiteMetricAccessRepository metricAccessRepository;
    @Mock
    private TurSearchEnginePluginFactory pluginFactory;
    @Mock
    private TurGlobalSettingsService globalSettingsService;
    @Mock
    private TurLLMInstanceRepository llmInstanceRepository;
    @Mock
    private TurGenAiLlmProviderFactory llmProviderFactory;
    @Mock
    private TurSecretCryptoService secretCryptoService;
    @Mock
    private TurSNSiteDataCollectorService siteDataCollectorService;
    @Mock
    private TurLlmCacheService llmCacheService;

    private TurWeeklySearchReportService service;

    @BeforeEach
    void setUp() {
        service = new TurWeeklySearchReportService(emailService, snSiteRepository,
                snSiteLocaleRepository, metricAccessRepository, pluginFactory,
                globalSettingsService, llmInstanceRepository, llmProviderFactory,
                secretCryptoService, siteDataCollectorService, llmCacheService);
    }

    @Test
    void sendWeeklyReportShouldCallEmailServiceWithGeneratedHtml() {
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(emailService.loadTemplate(anyString())).thenReturn("{{reportPeriod}} {{totalSearches}} {{siteCards}} {{generatedAt}}");

        service.sendWeeklyReport();

        verify(emailService).sendEmail(eq("Viglet Turing ES - Weekly Intelligence Report"), anyString());
    }

    @Test
    void sendWeeklyReportShouldNotThrowWhenEmailServiceFails() {
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(emailService.loadTemplate(anyString())).thenReturn("{{reportPeriod}} {{totalSearches}} {{siteCards}} {{generatedAt}}");
        doThrow(new RuntimeException("Send failed"))
                .when(emailService).sendEmail(anyString(), anyString());

        assertDoesNotThrow(() -> service.sendWeeklyReport());
    }

    @Test
    void buildReportHtmlShouldContainPeriodAndTotalSearches() {
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(emailService.loadTemplate(anyString())).thenReturn("Period:{{reportPeriod}} Total:{{totalSearches}} Cards:{{siteCards}} At:{{generatedAt}}");

        String html = service.buildReportHtml();

        assertTrue(html.contains("Total:0"));
        assertTrue(html.contains("Period:"));
        assertFalse(html.contains("{{reportPeriod}}"));
        assertFalse(html.contains("{{totalSearches}}"));
    }

    @Test
    void buildReportHtmlShouldIncludeSiteCards() {
        TurSNSite site = mock(TurSNSite.class);
        when(site.getName()).thenReturn("TestSite");
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(5);
        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);

        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        String siteCardTemplate = "{{siteName}}|{{siteInitial}}|{{siteSearchCount}}|{{siteDocumentCount}}|{{siteLocaleCount}}|{{topSearchRows}}|{{siteSummary}}";
        String reportTemplate = "{{reportPeriod}}|{{totalSearches}}|{{siteCards}}|{{generatedAt}}";

        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn(siteCardTemplate);
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn(reportTemplate);

        String html = service.buildReportHtml();

        assertTrue(html.contains("TestSite"));
        assertTrue(html.contains("|T|"));
        assertTrue(html.contains("|5|"));
    }

    @Test
    void buildReportHtmlShouldAccumulateTotalSearches() {
        TurSNSite site1 = mock(TurSNSite.class);
        when(site1.getName()).thenReturn("Site1");
        TurSNSite site2 = mock(TurSNSite.class);
        when(site2.getName()).thenReturn("Site2");

        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site1, site2));
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(metricAccessRepository.countTermsByPeriod(eq(site1), any(), any())).thenReturn(10);
        when(metricAccessRepository.countTermsByPeriod(eq(site2), any(), any())).thenReturn(20);
        when(snSiteLocaleRepository.findByTurSNSite(any())).thenReturn(Collections.emptyList());

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(pluginFactory.getPluginForSite(any())).thenReturn(plugin);
        when(metricAccessRepository.topTermsBetweenDates(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn("{{siteName}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn("Total:{{totalSearches}}");

        String html = service.buildReportHtml();

        assertTrue(html.contains("Total:30"));
    }

    @Test
    void buildReportHtmlShouldShowNoSearchesMessageWhenEmpty() {
        TurSNSite site = mock(TurSNSite.class);
        when(site.getName()).thenReturn("EmptySite");
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn("{{topSearchRows}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn("{{siteCards}}");

        String html = service.buildReportHtml();

        assertTrue(html.contains("No searches recorded this week"));
    }

    @Test
    void buildReportHtmlShouldBuildTopSearchRows() {
        TurSNSite site = mock(TurSNSite.class);
        when(site.getName()).thenReturn("MySite");
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(5);
        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);

        TurSNSiteMetricAccessTerm term = new TurSNSiteMetricAccessTerm("java", 3, 10.0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(List.of(term));

        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn("{{topSearchRows}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn("{{siteCards}}");

        String html = service.buildReportHtml();

        assertTrue(html.contains("java"));
        assertTrue(html.contains("3x"));
    }

    @Test
    void buildReportHtmlShouldHandlePluginExceptionGracefully() {
        TurSNSite site = mock(TurSNSite.class);
        when(site.getName()).thenReturn("BrokenSite");
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());
        when(pluginFactory.getPluginForSite(site)).thenThrow(new RuntimeException("plugin error"));
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn("{{siteDocumentCount}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn("{{siteCards}}");

        String html = service.buildReportHtml();

        assertTrue(html.contains("0"));
    }

    @Test
    void buildReportHtmlShouldEscapeHtmlInSiteName() {
        TurSNSite site = mock(TurSNSite.class);
        when(site.getName()).thenReturn("<script>alert('xss')</script>");
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn("{{siteName}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn("{{siteCards}}");

        String html = service.buildReportHtml();

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    void buildReportHtmlShouldSkipLlmWhenDefaultLlmIdEmpty() {
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(emailService.loadTemplate(anyString())).thenReturn("{{reportPeriod}}{{totalSearches}}{{siteCards}}{{generatedAt}}");

        service.buildReportHtml();

        verifyNoInteractions(llmInstanceRepository);
        verifyNoInteractions(llmProviderFactory);
    }

    @Test
    void buildReportHtmlShouldSkipLlmWhenInstanceNotFound() {
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.empty());
        when(emailService.loadTemplate(anyString())).thenReturn("{{reportPeriod}}{{totalSearches}}{{siteCards}}{{generatedAt}}");

        String html = service.buildReportHtml();

        assertNotNull(html);
        verifyNoInteractions(llmProviderFactory);
    }

    @Test
    void buildReportHtmlShouldCycleAccentColors() {
        // Create 11 sites to test color cycling (array has 10 colors)
        List<TurSNSite> sites = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) {
            TurSNSite site = mock(TurSNSite.class);
            when(site.getName()).thenReturn("Site" + i);
            sites.add(site);
        }

        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(sites);
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(metricAccessRepository.countTermsByPeriod(any(), any(), any())).thenReturn(0);
        when(snSiteLocaleRepository.findByTurSNSite(any())).thenReturn(Collections.emptyList());

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(pluginFactory.getPluginForSite(any())).thenReturn(plugin);
        when(metricAccessRepository.topTermsBetweenDates(any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn("{{siteAccentColor}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn("{{siteCards}}");

        String html = service.buildReportHtml();

        // First and 11th site should have the same color (#3b82f6)
        int firstColorCount = html.split("#3b82f6", -1).length - 1;
        assertEquals(2, firstColorCount, "First color should appear twice for 11 sites (index 0 and 10)");
    }
}
