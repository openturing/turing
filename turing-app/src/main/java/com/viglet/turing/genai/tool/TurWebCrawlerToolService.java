package com.viglet.turing.genai.tool;

import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class TurWebCrawlerToolService {

    private static final int TIMEOUT_MS = 15_000;
    private static final int MAX_BODY_LENGTH = 12_000;
    private static final int MAX_LINKS = 30;

    @Tool(name = "fetch_webpage", description = ".")
    public String fetchWebpage(String url, String includeLinks) {
        log.info("[WebCrawler Tool] fetch_webpage called: url={}, includeLinks={}", url, includeLinks);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; TuringBot/1.0)")
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();

            String title = doc.title();
            String bodyText = doc.body() != null ? doc.body().text() : "";

            if (bodyText.length() > MAX_BODY_LENGTH) {
                bodyText = bodyText.substring(0, MAX_BODY_LENGTH) + "\n... [content truncated]";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Title: ").append(title).append("\n");
            sb.append("URL: ").append(url).append("\n\n");
            sb.append(bodyText);

            if ("yes".equalsIgnoreCase(includeLinks)) {
                appendLinksToContent(sb, doc);
            }

            String result = sb.toString();
            log.info("[WebCrawler Tool] fetch_webpage: fetched {} chars from {}", result.length(), url);
            return result;
        } catch (IOException e) {
            log.error("[WebCrawler Tool] fetch_webpage failed for {}: {}", url, e.getMessage());
            return "Error fetching URL " + url + ": " + e.getMessage();
        }
    }

    private void appendLinksToContent(StringBuilder sb, Document doc) {
        sb.append("\n\n--- Links found on page ---\n");
        Elements links = doc.select("a[href]");
        int count = 0;
        for (Element link : links) {
            if (count >= MAX_LINKS) {
                sb.append("... [more links truncated]\n");
                break;
            }
            if (isValidLink(link)) {
                String href = link.absUrl("href");
                String text = link.text().strip();
                if (text.isEmpty())
                    text = href;
                sb.append("- ").append(text).append(" -> ").append(href).append("\n");
                count++;
            }
        }
    }

    private boolean isValidLink(Element link) {
        String href = link.absUrl("href");
        return !href.isEmpty() && !href.startsWith("javascript:");
    }

    private boolean isValidExtractLink(Element link, String filterKeyword) {
        String href = link.absUrl("href");
        if (href.isEmpty() || href.startsWith("javascript:")) {
            return false;
        }
        if (filterKeyword == null) {
            return true;
        }
        String text = link.text().strip().toLowerCase();
        return text.contains(filterKeyword) || href.toLowerCase().contains(filterKeyword);
    }

    @Tool(name = "extract_links", description = ".")
    public String extractLinks(String url, String filterKeyword) {
        log.info("[WebCrawler Tool] extract_links called: url={}, filter={}", url, filterKeyword);
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (compatible; TuringBot/1.0)")
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get();

            Elements links = doc.select("a[href]");
            StringBuilder sb = new StringBuilder();
            sb.append("Links from: ").append(url).append("\n\n");

            String filter = (filterKeyword != null && !filterKeyword.isBlank())
                    ? filterKeyword.toLowerCase()
                    : null;

            int count = 0;
            for (Element link : links) {
                if (count >= MAX_LINKS * 2) {
                    sb.append("... [more links truncated]\n");
                    break;
                }
                if (isValidExtractLink(link, filter)) {
                    String href = link.absUrl("href");
                    String text = link.text().strip();
                    if (text.isEmpty())
                        text = href;

                    sb.append("- ").append(text).append("\n  ").append(href).append("\n");
                    count++;
                }
            }

            if (count == 0) {
                sb.append("No links found");
                if (filter != null)
                    sb.append(" matching '").append(filterKeyword).append("'");
                sb.append(".");
            } else {
                sb.append("\nTotal: ").append(count).append(" links");
            }

            String result = sb.toString();
            log.info("[WebCrawler Tool] extract_links: found {} links from {}", count, url);
            return result;
        } catch (IOException e) {
            log.error("[WebCrawler Tool] extract_links failed for {}: {}", url, e.getMessage());
            return "Error fetching URL " + url + ": " + e.getMessage();
        }
    }
}
