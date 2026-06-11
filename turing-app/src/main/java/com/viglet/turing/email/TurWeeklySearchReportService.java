package com.viglet.turing.email;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import com.viglet.turing.resilience.llm.TurLlmModelFactory;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.llm.TurLLMInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;
import com.viglet.turing.service.llm.tokenusage.TurLLMTokenUsageService;
import com.viglet.turing.sn.TurSNSiteDataCollectorService;
import com.viglet.turing.system.TurGlobalSettingsService;
import com.viglet.turing.system.TurLlmCacheService;
import com.viglet.turing.system.security.TurSecretCryptoService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurWeeklySearchReportService {

    private static final int TOP_SEARCHES_LIMIT = 10;
    private static final String TEMPLATE_PATH = "templates/email/weekly-search-report.html";
    private static final String SITE_CARD_TEMPLATE_PATH = "templates/email/weekly-search-report-site-card.html";
    private static final String AI_INSIGHTS_TEMPLATE_PATH = "templates/email/weekly-search-report-ai-insights.html";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");

    private static final String[] SITE_ACCENT_COLORS = {
            "#3b82f6", "#6366f1", "#818cf8", "#60a5fa", "#a78bfa",
            "#38bdf8", "#34d399", "#93c5fd", "#c084fc", "#4ade80"
    };

    private final TurEmailService emailService;
    private final TurSNSiteRepository snSiteRepository;
    private final TurSNSiteLocaleRepository snSiteLocaleRepository;
    private final TurSNSiteMetricAccessRepository metricAccessRepository;
    private final TurSearchEnginePluginFactory pluginFactory;
    private final TurGlobalSettingsService globalSettingsService;
    private final TurLLMInstanceRepository llmInstanceRepository;
    private final TurLlmModelFactory llmModelFactory;
    private final TurSecretCryptoService secretCryptoService;
    private final TurSNSiteDataCollectorService siteDataCollectorService;
    private final TurLlmCacheService llmCacheService;
    private final TurLLMTokenUsageService tokenUsageService;
    private final com.viglet.turing.tenant.TurTenantScheduledFanOut tenantFanOut;

    public TurWeeklySearchReportService(TurEmailService emailService,
            TurSNSiteRepository snSiteRepository,
            TurSNSiteLocaleRepository snSiteLocaleRepository,
            TurSNSiteMetricAccessRepository metricAccessRepository,
            TurSearchEnginePluginFactory pluginFactory,
            TurGlobalSettingsService globalSettingsService,
            TurLLMInstanceRepository llmInstanceRepository,
            TurLlmModelFactory llmModelFactory,
            TurSecretCryptoService secretCryptoService,
            TurSNSiteDataCollectorService siteDataCollectorService,
            TurLlmCacheService llmCacheService,
            TurLLMTokenUsageService tokenUsageService,
            com.viglet.turing.tenant.TurTenantScheduledFanOut tenantFanOut) {
        this.tenantFanOut = tenantFanOut;
        this.emailService = emailService;
        this.snSiteRepository = snSiteRepository;
        this.snSiteLocaleRepository = snSiteLocaleRepository;
        this.metricAccessRepository = metricAccessRepository;
        this.pluginFactory = pluginFactory;
        this.globalSettingsService = globalSettingsService;
        this.llmInstanceRepository = llmInstanceRepository;
        this.llmModelFactory = llmModelFactory;
        this.secretCryptoService = secretCryptoService;
        this.siteDataCollectorService = siteDataCollectorService;
        this.llmCacheService = llmCacheService;
        this.tokenUsageService = tokenUsageService;
    }

    @Scheduled(cron = "0 0 8 1 * *")
    @SchedulerLock(name = "monthlySearchReport", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void sendWeeklyReport() {
        // T273 / §XIV.4.7 — one report per active tenant; buildReportHtml() reads
        // snSiteRepository.findAll() which is @TenantId-filtered to the bound tenant.
        tenantFanOut.forEachActiveTenant(this::sendReportForCurrentTenant);
    }

    private void sendReportForCurrentTenant() {
        log.info("Generating monthly search report...");
        try {
            String html = buildReportHtml();
            emailService.sendEmail("Viglet Turing ES - Monthly Intelligence Report", html);
            log.info("Monthly search report sent successfully.");
        } catch (Exception e) {
            log.error("Failed to send monthly search report", e);
        }
    }

    public String buildReportHtml() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime monthStart = now.toLocalDate().withDayOfMonth(1).minusMonths(1)
                .atStartOfDay(now.getZone());
        ZonedDateTime monthEnd = monthStart.plusMonths(1);

        Instant startInstant = monthStart.toInstant();
        Instant endInstant = monthEnd.toInstant();

        List<TurSNSite> sites = snSiteRepository.findAll(Sort.by("name"));

        // Resolve LLM once for all sites
        ChatModel chatModel = null;
        String llmModelName = null;
        TurLLMInstance resolvedLlmInstance = null;
        String defaultLlmId = globalSettingsService.getDefaultLlmId();
        if (StringUtils.hasText(defaultLlmId)) {
            TurLLMInstance llmInstance = llmInstanceRepository.findById(defaultLlmId).orElse(null);
            if (llmInstance != null && llmInstance.getEnabled() == 1) {
                resolvedLlmInstance = llmInstance;
                try {
                    String decryptedApiKey = secretCryptoService.decrypt(llmInstance.getApiKeyEncrypted());
                    chatModel = llmModelFactory.createChatModel(llmInstance, decryptedApiKey);
                    llmModelName = StringUtils.hasText(llmInstance.getModelName())
                            ? llmInstance.getTitle() + " (" + llmInstance.getModelName() + ")"
                            : llmInstance.getTitle();
                } catch (Exception e) {
                    log.warn("Could not create LLM chat model for weekly report", e);
                }
            }
        }

        StringBuilder siteCardsHtml = new StringBuilder();
        int totalSearches = 0;
        String siteCardTemplate = emailService.loadTemplate(SITE_CARD_TEMPLATE_PATH);

        for (int i = 0; i < sites.size(); i++) {
            TurSNSite site = sites.get(i);
            String accentColor = SITE_ACCENT_COLORS[i % SITE_ACCENT_COLORS.length];

            int siteSearchCount = metricAccessRepository.countTermsByPeriod(site, startInstant, endInstant);
            totalSearches += siteSearchCount;

            long documentCount = getDocumentCount(site);
            int localeCount = snSiteLocaleRepository.findByTurSNSite(site).size();

            List<TurSNSiteMetricAccessTerm> topTerms = metricAccessRepository
                    .topTermsBetweenDates(site, startInstant, endInstant, PageRequest.of(0, TOP_SEARCHES_LIMIT));

            String topSearchRows = buildTopSearchRows(topTerms, accentColor);
            String siteSummaryHtml = generateSiteSummary(site, chatModel, llmModelName, resolvedLlmInstance);

            String siteCard = siteCardTemplate
                    .replace("{{siteName}}", escapeHtml(site.getName()))
                    .replace("{{siteInitial}}", escapeHtml(site.getName().substring(0, 1).toUpperCase(Locale.ROOT)))
                    .replace("{{siteAccentColor}}", accentColor)
                    .replace("{{siteSearchCount}}", formatNumber(siteSearchCount))
                    .replace("{{siteDocumentCount}}", formatNumber(documentCount))
                    .replace("{{siteLocaleCount}}", formatNumber(localeCount))
                    .replace("{{topSearchRows}}", topSearchRows)
                    .replace("{{siteSummary}}", siteSummaryHtml);

            siteCardsHtml.append(siteCard);
        }

        String reportPeriod = monthStart.format(DATE_FORMAT) + " - " + monthEnd.minusDays(1).format(DATE_FORMAT);

        String reportHtml = emailService.loadTemplate(TEMPLATE_PATH);
        return reportHtml
                .replace("{{reportPeriod}}", reportPeriod)
                .replace("{{totalSearches}}", formatNumber(totalSearches))
                .replace("{{siteCards}}", siteCardsHtml.toString())
                .replace("{{generatedAt}}", now.format(DATETIME_FORMAT));
    }

    private String generateSiteSummary(TurSNSite site, ChatModel chatModel, String llmModelName,
            TurLLMInstance llmInstance) {
        if (chatModel == null) {
            return "";
        }

        String cached = llmCacheService.get(site.getId());
        if (cached != null) {
            log.debug("Using cached LLM summary for site {}", site.getName());
            String aiTemplate = emailService.loadTemplate(AI_INSIGHTS_TEMPLATE_PATH);
            return aiTemplate
                    .replace("{{aiInsightsContent}}", cached)
                    .replace("{{llmModelName}}", escapeHtml(llmModelName) + " (cached)");
        }

        try {
            String siteData = siteDataCollectorService.collectSiteData(site);

            List<Message> messages = List.of(
                    new SystemMessage(
                            """
                                    You are an enterprise search expert analyzing a Semantic Navigation site from the Turing platform. \
                                    Based on the site data provided, generate a comprehensive summary in HTML format. \
                                    Include these sections with <h3> headings:
                                    <h3>Overview</h3> Brief summary of the site configuration and health.
                                    <h3>Search Activity</h3> Analysis of search metrics and top terms.
                                    <h3>Configuration Review</h3> Review of fields, locales, facets, and behavior settings.
                                    <h3>Suggestions</h3> Actionable recommendations to improve search quality and user experience.

                                    Use <p>, <strong>, <ul>/<li> tags for formatting. \
                                    Be concise but insightful. Use bullet points where appropriate. \
                                    Highlight any potential issues or misconfigurations. \
                                    Do NOT include <html>, <head>, <body>, or <style> tags. \
                                    Use inline styles with colors suitable for a dark background \
                                    (text: #cbd5e1, headings: #f1f5f9, accent/bold: #60a5fa, secondary: #94a3b8, \
                                    list bullets: #6366f1). Style <h3> with: color:#f1f5f9;font-size:14px;font-weight:700;margin:16px 0 8px;"""),
                    new UserMessage(siteData
                            + "\nPlease analyze all this data and provide a comprehensive summary with suggestions."));

            Prompt prompt = new Prompt(messages);
            var response = chatModel.call(prompt);

            if (llmInstance != null) {
                tokenUsageService.recordUsage(llmInstance, response, "system-scheduler");
            }

            String aiContent = response.getResult().getOutput().getText();

            if (!StringUtils.hasText(aiContent)) {
                return "";
            }

            llmCacheService.put(site.getId(), aiContent);

            String aiTemplate = emailService.loadTemplate(AI_INSIGHTS_TEMPLATE_PATH);
            return aiTemplate
                    .replace("{{aiInsightsContent}}", aiContent)
                    .replace("{{llmModelName}}", escapeHtml(llmModelName));

        } catch (Exception e) {
            log.warn("Failed to generate AI summary for site {}", site.getName(), e);
            return "";
        }
    }

    private long getDocumentCount(TurSNSite site) {
        try {
            var plugin = pluginFactory.getPluginForSite(site);
            return snSiteLocaleRepository.findByTurSNSite(site).stream()
                    .mapToLong(locale -> {
                        try {
                            return plugin.getDocumentTotal(locale);
                        } catch (Exception e) {
                            log.warn("Could not get document count for site {} locale {}",
                                    site.getName(), locale.getLanguage(), e);
                            return 0L;
                        }
                    })
                    .sum();
        } catch (Exception e) {
            log.warn("Could not get plugin for site {}", site.getName(), e);
            return 0L;
        }
    }

    private String buildTopSearchRows(List<TurSNSiteMetricAccessTerm> topTerms, String accentColor) {
        if (topTerms.isEmpty()) {
            return "<tr><td style=\"padding:14px;text-align:center;color:#475569;font-size:12px;font-style:italic;\">"
                    + "No searches recorded this month</td></tr>";
        }

        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < topTerms.size(); i++) {
            TurSNSiteMetricAccessTerm term = topTerms.get(i);
            String bgColor = (i % 2 == 0) ? "#0d0d14" : "#111118";
            String borderRadius = "";
            if (i == 0) {
                borderRadius = "border-radius:12px 12px 0 0;";
            } else if (i == topTerms.size() - 1) {
                borderRadius = "border-radius:0 0 12px 12px;";
            }

            rows.append(
                    """
                            <tr>
                            <td style="padding:9px 14px;background:%s;%s">
                            <table role="presentation" cellspacing="0" cellpadding="0" border="0" width="100%%"><tr>
                            <td width="26" style="vertical-align:middle;">
                            <div style="background:%s;width:22px;height:22px;border-radius:6px;text-align:center;line-height:22px;font-size:10px;font-weight:800;color:#0a0a0f;">
                            %d
                            </div>
                            </td>
                            <td style="padding-left:10px;vertical-align:middle;">
                            <span style="font-size:13px;color:#e2e8f0;font-weight:500;">%s</span>
                            </td>
                            <td align="right" style="vertical-align:middle;">
                            <span style="font-size:11px;color:#64748b;background:#16161f;border-radius:4px;padding:3px 7px;">
                            %dx
                            </span>
                            </td>
                            </tr></table>
                            </td>
                            </tr>
                            """
                            .formatted(bgColor, borderRadius, accentColor, i + 1, escapeHtml(term.getTerm()),
                                    term.getTotal()));
        }

        return rows.toString();
    }

    private static String formatNumber(long number) {
        return NumberFormat.getInstance(Locale.US).format(number);
    }

    private static String escapeHtml(String text) {
        if (text == null)
            return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
