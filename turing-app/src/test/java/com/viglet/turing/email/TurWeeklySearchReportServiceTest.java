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

import com.viglet.turing.resilience.llm.TurLlmModelFactory;
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
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
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
    private TurLlmModelFactory llmModelFactory;
    @Mock
    private TurSecretCryptoService secretCryptoService;
    @Mock
    private TurSNSiteDataCollectorService siteDataCollectorService;
    @Mock
    private TurLlmCacheService llmCacheService;
    @Mock
    private TurLLMTokenUsageService tokenUsageService;

    private TurWeeklySearchReportService service;

    @BeforeEach
    void setUp() {
        com.viglet.turing.properties.TurConfigProperties props =
                new com.viglet.turing.properties.TurConfigProperties();
        com.viglet.turing.tenant.TurTenantScheduledFanOut fanOut =
                new com.viglet.turing.tenant.TurTenantScheduledFanOut(
                        new com.viglet.turing.tenant.TurTenantContext(props),
                        org.mockito.Mockito.mock(
                                com.viglet.turing.persistence.repository.tenant.TurTenantRepository.class));
        service = new TurWeeklySearchReportService(emailService, snSiteRepository,
                snSiteLocaleRepository, metricAccessRepository, pluginFactory,
                globalSettingsService, llmInstanceRepository, llmModelFactory,
                secretCryptoService, siteDataCollectorService, llmCacheService,
                tokenUsageService, fanOut);
    }

    @Test
    void sendWeeklyReportShouldCallEmailServiceWithGeneratedHtml() {
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(emailService.loadTemplate(anyString())).thenReturn("{{reportPeriod}} {{totalSearches}} {{siteCards}} {{generatedAt}}");

        service.sendWeeklyReport();

        verify(emailService).sendEmail(eq("Viglet Turing ES - Monthly Intelligence Report"), anyString());
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

        assertTrue(html.contains("No searches recorded this month"));
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
        verifyNoInteractions(llmModelFactory);
    }

    @Test
    void buildReportHtmlShouldSkipLlmWhenInstanceNotFound() {
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.empty());
        when(emailService.loadTemplate(anyString())).thenReturn("{{reportPeriod}}{{totalSearches}}{{siteCards}}{{generatedAt}}");

        String html = service.buildReportHtml();

        assertNotNull(html);
        verifyNoInteractions(llmModelFactory);
    }

    @Test
    void buildReportHtmlShouldUseCachedLlmSummary() {
        TurSNSite site = mock(TurSNSite.class);
        when(site.getName()).thenReturn("CachedSite");
        when(site.getId()).thenReturn("site-cache-1");
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Setup LLM with cached summary
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-1");
        com.viglet.turing.persistence.model.llm.TurLLMInstance llmInstance =
                new com.viglet.turing.persistence.model.llm.TurLLMInstance();
        llmInstance.setEnabled(1);
        llmInstance.setTitle("GPT-4");
        llmInstance.setModelName("gpt-4-turbo");
        llmInstance.setApiKeyEncrypted("enc-key");
        when(llmInstanceRepository.findById("llm-1")).thenReturn(Optional.of(llmInstance));

        var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
        when(llmModelFactory.createChatModel(eq(llmInstance), any())).thenReturn(chatModel);
        when(secretCryptoService.decrypt("enc-key")).thenReturn("decrypted-key");

        // Return cached content
        when(llmCacheService.get("site-cache-1")).thenReturn("<p>Cached AI insight</p>");

        when(emailService.loadTemplate("templates/email/weekly-search-report-ai-insights.html"))
                .thenReturn("AI:{{aiInsightsContent}}|Model:{{llmModelName}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn("{{siteSummary}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn("{{siteCards}}");

        String html = service.buildReportHtml();

        assertTrue(html.contains("Cached AI insight"));
        assertTrue(html.contains("(cached)"));
    }

    @Test
    void buildReportHtmlShouldGenerateLlmSummaryWhenNotCached() {
        TurSNSite site = mock(TurSNSite.class);
        when(site.getName()).thenReturn("LiveSite");
        when(site.getId()).thenReturn("site-live-1");
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Setup LLM
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-2");
        com.viglet.turing.persistence.model.llm.TurLLMInstance llmInstance =
                new com.viglet.turing.persistence.model.llm.TurLLMInstance();
        llmInstance.setEnabled(1);
        llmInstance.setTitle("Claude");
        llmInstance.setModelName(null);
        llmInstance.setApiKeyEncrypted("enc-key-2");
        when(llmInstanceRepository.findById("llm-2")).thenReturn(Optional.of(llmInstance));

        var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
        when(llmModelFactory.createChatModel(eq(llmInstance), any())).thenReturn(chatModel);
        when(secretCryptoService.decrypt("enc-key-2")).thenReturn("decrypted-key");

        // No cache
        when(llmCacheService.get("site-live-1")).thenReturn(null);

        // Mock chat call
        when(siteDataCollectorService.collectSiteData(site)).thenReturn("Site data for analysis");

        var chatResponse = mock(org.springframework.ai.chat.model.ChatResponse.class);
        var generation = mock(org.springframework.ai.chat.model.Generation.class);
        var assistantMessage = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn("<h3>Overview</h3><p>Analysis here</p>");

        when(emailService.loadTemplate("templates/email/weekly-search-report-ai-insights.html"))
                .thenReturn("AI:{{aiInsightsContent}}|Model:{{llmModelName}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn("{{siteSummary}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn("{{siteCards}}");

        String html = service.buildReportHtml();

        assertTrue(html.contains("Analysis here"));
        assertTrue(html.contains("Claude"));
        verify(llmCacheService).put(eq("site-live-1"), any());
    }

    @Test
    void buildReportHtmlShouldHandleLlmExceptionGracefully() {
        TurSNSite site = mock(TurSNSite.class);
        when(site.getName()).thenReturn("ErrorLlmSite");
        when(site.getId()).thenReturn("site-err-1");
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        // Setup LLM that throws during summary
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-3");
        com.viglet.turing.persistence.model.llm.TurLLMInstance llmInstance =
                new com.viglet.turing.persistence.model.llm.TurLLMInstance();
        llmInstance.setEnabled(1);
        llmInstance.setTitle("Broken LLM");
        llmInstance.setModelName("broken");
        llmInstance.setApiKeyEncrypted("enc-key-3");
        when(llmInstanceRepository.findById("llm-3")).thenReturn(Optional.of(llmInstance));

        var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
        when(llmModelFactory.createChatModel(eq(llmInstance), any())).thenReturn(chatModel);
        when(secretCryptoService.decrypt("enc-key-3")).thenReturn("decrypted-key");

        when(llmCacheService.get("site-err-1")).thenReturn(null);
        when(siteDataCollectorService.collectSiteData(site)).thenThrow(new RuntimeException("LLM failed"));

        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn("{{siteSummary}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn("{{siteCards}}");

        String html = service.buildReportHtml();

        assertNotNull(html);
    }

    @Test
    void buildReportHtmlShouldSkipDisabledLlmInstance() {
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-disabled");
        com.viglet.turing.persistence.model.llm.TurLLMInstance llmInstance =
                new com.viglet.turing.persistence.model.llm.TurLLMInstance();
        llmInstance.setEnabled(0);
        when(llmInstanceRepository.findById("llm-disabled")).thenReturn(Optional.of(llmInstance));
        when(emailService.loadTemplate(anyString())).thenReturn("{{reportPeriod}}{{totalSearches}}{{siteCards}}{{generatedAt}}");

        String html = service.buildReportHtml();

        assertNotNull(html);
        verifyNoInteractions(llmModelFactory);
    }

    @Test
    void buildReportHtmlShouldBuildTopSearchRowsWithMultipleTerms() {
        TurSNSite site = mock(TurSNSite.class);
        when(site.getName()).thenReturn("MultiTermSite");
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(15);
        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);

        // 3 terms to test first/middle/last border-radius logic
        TurSNSiteMetricAccessTerm term1 = new TurSNSiteMetricAccessTerm("java", 10, 50.0);
        TurSNSiteMetricAccessTerm term2 = new TurSNSiteMetricAccessTerm("spring", 7, 30.0);
        TurSNSiteMetricAccessTerm term3 = new TurSNSiteMetricAccessTerm("python", 3, 20.0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(List.of(term1, term2, term3));

        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn("{{topSearchRows}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn("{{siteCards}}");

        String html = service.buildReportHtml();

        assertTrue(html.contains("java"));
        assertTrue(html.contains("spring"));
        assertTrue(html.contains("python"));
        assertTrue(html.contains("10x"));
        assertTrue(html.contains("7x"));
        assertTrue(html.contains("3x"));
        // First term should have top border-radius
        assertTrue(html.contains("border-radius:12px 12px 0 0"));
        // Last term should have bottom border-radius
        assertTrue(html.contains("border-radius:0 0 12px 12px"));
    }

    @Test
    void buildReportHtmlShouldCountDocumentsFromMultipleLocales() {
        TurSNSite site = mock(TurSNSite.class);
        when(site.getName()).thenReturn("MultiLocaleSite");
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        var enLocale = mock(com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale.class);
        var ptLocale = mock(com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale.class);
        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(enLocale, ptLocale));

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.getDocumentTotal(enLocale)).thenReturn(1000L);
        when(plugin.getDocumentTotal(ptLocale)).thenReturn(500L);

        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn("{{siteDocumentCount}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn("{{siteCards}}");

        String html = service.buildReportHtml();

        // 1000 + 500 = 1500
        assertTrue(html.contains("1,500"));
    }

    @Test
    void buildReportHtmlShouldHandleLocaleDocumentCountException() {
        TurSNSite site = mock(TurSNSite.class);
        when(site.getName()).thenReturn("LocaleErrSite");
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(globalSettingsService.getDefaultLlmId()).thenReturn("");
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        var enLocale = mock(com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale.class);
        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(List.of(enLocale));

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(plugin.getDocumentTotal(enLocale)).thenThrow(new RuntimeException("Solr down"));

        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn("{{siteDocumentCount}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn("{{siteCards}}");

        String html = service.buildReportHtml();

        assertTrue(html.contains("0"));
    }

    @Test
    void buildReportHtmlShouldHandleLlmProviderCreationException() {
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(Collections.emptyList());
        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-fail");
        com.viglet.turing.persistence.model.llm.TurLLMInstance llmInstance =
                new com.viglet.turing.persistence.model.llm.TurLLMInstance();
        llmInstance.setEnabled(1);
        llmInstance.setTitle("Failing LLM");
        llmInstance.setApiKeyEncrypted("enc");
        when(llmInstanceRepository.findById("llm-fail")).thenReturn(Optional.of(llmInstance));
        when(llmModelFactory.createChatModel(eq(llmInstance), any())).thenThrow(new RuntimeException("Provider init failed"));
        when(emailService.loadTemplate(anyString())).thenReturn("{{reportPeriod}}{{totalSearches}}{{siteCards}}{{generatedAt}}");

        String html = service.buildReportHtml();

        assertNotNull(html);
        // Should still generate report without AI summaries
    }

    @Test
    void buildReportHtmlShouldHandleEmptyAiContent() {
        TurSNSite site = mock(TurSNSite.class);
        when(site.getName()).thenReturn("EmptyAiSite");
        when(site.getId()).thenReturn("site-empty-ai");
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-empty");
        com.viglet.turing.persistence.model.llm.TurLLMInstance llmInstance =
                new com.viglet.turing.persistence.model.llm.TurLLMInstance();
        llmInstance.setEnabled(1);
        llmInstance.setTitle("EmptyLLM");
        llmInstance.setModelName("empty-model");
        llmInstance.setApiKeyEncrypted("enc-empty");
        when(llmInstanceRepository.findById("llm-empty")).thenReturn(Optional.of(llmInstance));

        var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
        when(llmModelFactory.createChatModel(eq(llmInstance), any())).thenReturn(chatModel);
        when(secretCryptoService.decrypt("enc-empty")).thenReturn("key");

        when(llmCacheService.get("site-empty-ai")).thenReturn(null);
        when(siteDataCollectorService.collectSiteData(site)).thenReturn("data");

        var chatResponse = mock(org.springframework.ai.chat.model.ChatResponse.class);
        var generation = mock(org.springframework.ai.chat.model.Generation.class);
        var assistantMessage = mock(org.springframework.ai.chat.messages.AssistantMessage.class);
        when(chatModel.call(any(org.springframework.ai.chat.prompt.Prompt.class))).thenReturn(chatResponse);
        when(chatResponse.getResult()).thenReturn(generation);
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn(""); // empty AI content

        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn("Summary:{{siteSummary}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn("{{siteCards}}");

        String html = service.buildReportHtml();

        // Empty AI content should result in empty summary
        assertTrue(html.contains("Summary:"));
        assertFalse(html.contains("{{siteSummary}}"));
    }

    @Test
    void buildReportHtmlShouldUseLlmTitleWithModelName() {
        TurSNSite site = mock(TurSNSite.class);
        when(site.getName()).thenReturn("ModelNameSite");
        when(site.getId()).thenReturn("site-mn");
        when(snSiteRepository.findAll(any(Sort.class))).thenReturn(List.of(site));
        when(metricAccessRepository.countTermsByPeriod(eq(site), any(), any())).thenReturn(0);
        when(snSiteLocaleRepository.findByTurSNSite(site)).thenReturn(Collections.emptyList());

        TurSearchEnginePlugin plugin = mock(TurSearchEnginePlugin.class);
        when(pluginFactory.getPluginForSite(site)).thenReturn(plugin);
        when(metricAccessRepository.topTermsBetweenDates(eq(site), any(), any(), any()))
                .thenReturn(Collections.emptyList());

        when(globalSettingsService.getDefaultLlmId()).thenReturn("llm-mn");
        com.viglet.turing.persistence.model.llm.TurLLMInstance llmInstance =
                new com.viglet.turing.persistence.model.llm.TurLLMInstance();
        llmInstance.setEnabled(1);
        llmInstance.setTitle("OpenAI");
        llmInstance.setModelName("gpt-4o");
        llmInstance.setApiKeyEncrypted("enc-mn");
        when(llmInstanceRepository.findById("llm-mn")).thenReturn(Optional.of(llmInstance));

        var chatModel = mock(org.springframework.ai.chat.model.ChatModel.class);
        when(llmModelFactory.createChatModel(eq(llmInstance), any())).thenReturn(chatModel);
        when(secretCryptoService.decrypt("enc-mn")).thenReturn("key");

        // Return cached content to test model name format
        when(llmCacheService.get("site-mn")).thenReturn("<p>cached</p>");

        when(emailService.loadTemplate("templates/email/weekly-search-report-ai-insights.html"))
                .thenReturn("{{llmModelName}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report-site-card.html"))
                .thenReturn("{{siteSummary}}");
        when(emailService.loadTemplate("templates/email/weekly-search-report.html"))
                .thenReturn("{{siteCards}}");

        String html = service.buildReportHtml();

        // Should contain "OpenAI (gpt-4o) (cached)"
        assertTrue(html.contains("OpenAI (gpt-4o)"));
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
