package com.viglet.turing.email;

import java.text.NumberFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessTerm;
import com.viglet.turing.plugins.se.TurSearchEnginePluginFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurWeeklySearchReportService {

    private static final int TOP_SEARCHES_LIMIT = 10;
    private static final String TEMPLATE_PATH = "templates/email/weekly-search-report.html";
    private static final String SITE_CARD_TEMPLATE_PATH = "templates/email/weekly-search-report-site-card.html";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");

    private final TurEmailService emailService;
    private final TurSNSiteRepository snSiteRepository;
    private final TurSNSiteLocaleRepository snSiteLocaleRepository;
    private final TurSNSiteMetricAccessRepository metricAccessRepository;
    private final TurSearchEnginePluginFactory pluginFactory;

    public TurWeeklySearchReportService(TurEmailService emailService,
            TurSNSiteRepository snSiteRepository,
            TurSNSiteLocaleRepository snSiteLocaleRepository,
            TurSNSiteMetricAccessRepository metricAccessRepository,
            TurSearchEnginePluginFactory pluginFactory) {
        this.emailService = emailService;
        this.snSiteRepository = snSiteRepository;
        this.snSiteLocaleRepository = snSiteLocaleRepository;
        this.metricAccessRepository = metricAccessRepository;
        this.pluginFactory = pluginFactory;
    }

    @Scheduled(cron = "0 0 8 * * MON")
    public void sendWeeklyReport() {
        log.info("Generating weekly search report...");
        try {
            String html = buildReportHtml();
            emailService.sendEmail("Turing Weekly Search Report", html);
            log.info("Weekly search report sent successfully.");
        } catch (Exception e) {
            log.error("Failed to send weekly search report", e);
        }
    }

    public String buildReportHtml() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        ZonedDateTime weekStart = now.with(TemporalAdjusters.previous(DayOfWeek.MONDAY)).toLocalDate()
                .atStartOfDay(now.getZone());
        ZonedDateTime weekEnd = weekStart.plusDays(7);

        Instant startInstant = weekStart.toInstant();
        Instant endInstant = weekEnd.toInstant();

        List<TurSNSite> sites = snSiteRepository.findAll(Sort.by("name"));

        StringBuilder siteCardsHtml = new StringBuilder();
        int totalSearches = 0;

        String siteCardTemplate = emailService.loadTemplate(SITE_CARD_TEMPLATE_PATH);

        for (TurSNSite site : sites) {
            int siteSearchCount = metricAccessRepository.countTermsByPeriod(site, startInstant, endInstant);
            totalSearches += siteSearchCount;

            long documentCount = getDocumentCount(site);

            List<TurSNSiteMetricAccessTerm> topTerms = metricAccessRepository
                    .topTermsBetweenDates(site, startInstant, endInstant, PageRequest.of(0, TOP_SEARCHES_LIMIT));

            String topSearchRows = buildTopSearchRows(topTerms);

            String siteCard = siteCardTemplate
                    .replace("{{siteName}}", escapeHtml(site.getName()))
                    .replace("{{siteInitial}}", escapeHtml(site.getName().substring(0, 1).toUpperCase(Locale.ROOT)))
                    .replace("{{siteSearchCount}}", formatNumber(siteSearchCount))
                    .replace("{{siteDocumentCount}}", formatNumber(documentCount))
                    .replace("{{topSearchRows}}", topSearchRows);

            siteCardsHtml.append(siteCard);
        }

        String reportPeriod = weekStart.format(DATE_FORMAT) + " - " + weekEnd.minusDays(1).format(DATE_FORMAT);

        String reportHtml = emailService.loadTemplate(TEMPLATE_PATH);
        return reportHtml
                .replace("{{reportPeriod}}", reportPeriod)
                .replace("{{totalSearches}}", formatNumber(totalSearches))
                .replace("{{siteCards}}", siteCardsHtml.toString())
                .replace("{{generatedAt}}", now.format(DATETIME_FORMAT));
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

    private String buildTopSearchRows(List<TurSNSiteMetricAccessTerm> topTerms) {
        if (topTerms.isEmpty()) {
            return "<tr><td style=\"padding:16px;text-align:center;color:#64748b;font-size:13px;font-style:italic;\">"
                    + "No searches recorded this week</td></tr>";
        }

        StringBuilder rows = new StringBuilder();
        String[] colors = {
                "#818cf8", "#a78bfa", "#c084fc", "#e879f9", "#f472b6",
                "#fb7185", "#fb923c", "#fbbf24", "#a3e635", "#34d399"
        };

        for (int i = 0; i < topTerms.size(); i++) {
            TurSNSiteMetricAccessTerm term = topTerms.get(i);
            String bgColor = (i % 2 == 0) ? "#0f172a" : "#111827";
            String accentColor = colors[i % colors.length];
            String borderRadius = "";
            if (i == 0) {
                borderRadius = "border-radius:12px 12px 0 0;";
            } else if (i == topTerms.size() - 1) {
                borderRadius = "border-radius:0 0 12px 12px;";
            }

            rows.append("<tr>")
                    .append("<td style=\"padding:10px 16px;background:").append(bgColor).append(";").append(borderRadius).append("\">")
                    .append("<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" width=\"100%\"><tr>")
                    .append("<td width=\"28\" style=\"vertical-align:middle;\">")
                    .append("<div style=\"background:").append(accentColor).append(";width:24px;height:24px;border-radius:6px;text-align:center;line-height:24px;font-size:11px;font-weight:700;color:#0f172a;\">")
                    .append(i + 1)
                    .append("</div>")
                    .append("</td>")
                    .append("<td style=\"padding-left:12px;vertical-align:middle;\">")
                    .append("<span style=\"font-size:14px;color:#e2e8f0;font-weight:500;\">").append(escapeHtml(term.getTerm())).append("</span>")
                    .append("</td>")
                    .append("<td align=\"right\" style=\"vertical-align:middle;\">")
                    .append("<span style=\"font-size:12px;color:#94a3b8;background:#1e293b;border-radius:6px;padding:4px 8px;\">")
                    .append(term.getTotal()).append(" searches")
                    .append("</span>")
                    .append("</td>")
                    .append("</tr></table>")
                    .append("</td>")
                    .append("</tr>");
        }

        return rows.toString();
    }

    private static String formatNumber(long number) {
        return NumberFormat.getInstance(Locale.US).format(number);
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
