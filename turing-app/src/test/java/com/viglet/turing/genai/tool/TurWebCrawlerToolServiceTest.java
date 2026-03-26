package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TurWebCrawlerToolServiceTest {

    private TurWebCrawlerToolService service;
    private Method isValidLinkMethod;
    private Method isValidExtractLinkMethod;

    @BeforeEach
    void setUp() throws Exception {
        service = new TurWebCrawlerToolService();
        isValidLinkMethod = TurWebCrawlerToolService.class.getDeclaredMethod("isValidLink", Element.class);
        isValidLinkMethod.setAccessible(true);
        isValidExtractLinkMethod = TurWebCrawlerToolService.class.getDeclaredMethod(
                "isValidExtractLink", Element.class, String.class);
        isValidExtractLinkMethod.setAccessible(true);
    }

    @Test
    void shouldAcceptValidLink() throws Exception {
        Document doc = Jsoup.parse("<a href='https://example.com/page'>Link</a>", "https://example.com");
        Element link = doc.select("a").first();

        boolean result = (boolean) isValidLinkMethod.invoke(service, link);

        assertThat(result).isTrue();
    }

    @Test
    void shouldRejectJavascriptLink() throws Exception {
        Document doc = Jsoup.parse("<a href='javascript:void(0)'>Link</a>", "https://example.com");
        Element link = doc.select("a").first();

        boolean result = (boolean) isValidLinkMethod.invoke(service, link);

        assertThat(result).isFalse();
    }

    @Test
    void shouldAcceptEmptyHrefLinkResolvedToBaseUrl() throws Exception {
        Document doc = Jsoup.parse("<a href=''>Link</a>", "https://example.com");
        Element link = doc.select("a").first();

        // Jsoup resolves empty href to the base URL, so absUrl returns a valid URL
        boolean result = (boolean) isValidLinkMethod.invoke(service, link);

        assertThat(result).isTrue();
    }

    @Test
    void shouldRejectLinkWithNoHrefAttribute() throws Exception {
        Document doc = Jsoup.parse("<a>Link</a>", "https://example.com");
        Element link = doc.select("a").first();

        boolean result = (boolean) isValidLinkMethod.invoke(service, link);

        assertThat(result).isFalse();
    }

    @ParameterizedTest
    @CsvSource(value = {
            "'<a href=\"https://example.com/page\">Link</a>','https://example.com',null,true",
            "'<a href=\"https://example.com\">Documentation</a>','https://example.com','documentation',true",
            "'<a href=\"https://example.com/docs/api\">Link</a>','https://example.com','docs',true",
            "'<a href=\"https://example.com/about\">About</a>','https://example.com','pricing',false"
    }, nullValues = "null")
    void shouldValidateExtractLink(String html, String baseUrl, String filter, boolean expected) throws Exception {
        Document doc = Jsoup.parse(html, baseUrl);
        Element link = doc.select("a").first();

        boolean result = (boolean) isValidExtractLinkMethod.invoke(service, link, filter);

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void shouldRejectJavascriptExtractLink() throws Exception {
        Document doc = Jsoup.parse("<a href='javascript:alert(1)'>Click</a>", "https://example.com");
        Element link = doc.select("a").first();

        boolean result = (boolean) isValidExtractLinkMethod.invoke(service, link, null);

        assertThat(result).isFalse();
    }

    @Test
    void shouldAcceptEmptyHrefExtractLinkResolvedToBaseUrl() throws Exception {
        Document doc = Jsoup.parse("<a href=''>Empty</a>", "https://example.com");
        Element link = doc.select("a").first();

        // Jsoup resolves empty href to the base URL
        boolean result = (boolean) isValidExtractLinkMethod.invoke(service, link, null);

        assertThat(result).isTrue();
    }

    @Test
    void shouldRejectExtractLinkWithNoHrefAttribute() throws Exception {
        Document doc = Jsoup.parse("<a>Link</a>", "https://example.com");
        Element link = doc.select("a").first();

        boolean result = (boolean) isValidExtractLinkMethod.invoke(service, link, null);

        assertThat(result).isFalse();
    }

    @Test
    void shouldMatchExtractLinkByHrefKeyword() throws Exception {
        Document doc = Jsoup.parse(
                "<a href='https://example.com/documentation/api'>API</a>", "https://example.com");
        Element link = doc.select("a").first();

        boolean result = (boolean) isValidExtractLinkMethod.invoke(service, link, "documentation");

        assertThat(result).isTrue();
    }

    @Test
    void shouldNotMatchExtractLinkWhenKeywordNotInTextOrHref() throws Exception {
        Document doc = Jsoup.parse(
                "<a href='https://example.com/about'>About Us</a>", "https://example.com");
        Element link = doc.select("a").first();

        boolean result = (boolean) isValidExtractLinkMethod.invoke(service, link, "pricing");

        assertThat(result).isFalse();
    }

    @Test
    void fetchWebpageShouldReturnErrorForInvalidUrl() {
        String result = service.fetchWebpage("http://invalid-nonexistent-domain-99999.com", "no");
        assertThat(result).contains("Error fetching URL");
    }

    @Test
    void fetchWebpageShouldReturnErrorForInvalidUrlWithLinks() {
        String result = service.fetchWebpage("http://invalid-nonexistent-domain-99999.com", "yes");
        assertThat(result).contains("Error fetching URL");
    }

    @Test
    void extractLinksShouldReturnErrorForInvalidUrl() {
        String result = service.extractLinks("http://invalid-nonexistent-domain-99999.com", null);
        assertThat(result).contains("Error fetching URL");
    }

    @Test
    void extractLinksShouldReturnErrorForInvalidUrlWithFilter() {
        String result = service.extractLinks("http://invalid-nonexistent-domain-99999.com", "docs");
        assertThat(result).contains("Error fetching URL");
    }

    @Test
    void appendLinksToContentShouldHandleDocument() throws Exception {
        java.lang.reflect.Method method = TurWebCrawlerToolService.class.getDeclaredMethod(
                "appendLinksToContent", StringBuilder.class, Document.class);
        method.setAccessible(true);

        Document doc = Jsoup.parse(
                "<html><body>"
                + "<a href='https://example.com/page1'>Page 1</a>"
                + "<a href='https://example.com/page2'>Page 2</a>"
                + "<a href='javascript:void(0)'>JS Link</a>"
                + "</body></html>",
                "https://example.com");

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, doc);

        String result = sb.toString();
        assertThat(result)
                .contains("Links found on page")
                .contains("Page 1")
                .contains("Page 2")
                .doesNotContain("JS Link");
    }

    @Test
    void appendLinksToContentShouldTruncateAtMaxLinks() throws Exception {
        java.lang.reflect.Method method = TurWebCrawlerToolService.class.getDeclaredMethod(
                "appendLinksToContent", StringBuilder.class, Document.class);
        method.setAccessible(true);

        StringBuilder html = new StringBuilder("<html><body>");
        for (int i = 0; i < 35; i++) {
            html.append("<a href='https://example.com/page").append(i).append("'>Link ").append(i).append("</a>");
        }
        html.append("</body></html>");

        Document doc = Jsoup.parse(html.toString(), "https://example.com");

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, doc);

        String result = sb.toString();
        assertThat(result).contains("[more links truncated]");
    }

    @Test
    void appendLinksToContentShouldUseHrefForEmptyText() throws Exception {
        java.lang.reflect.Method method = TurWebCrawlerToolService.class.getDeclaredMethod(
                "appendLinksToContent", StringBuilder.class, Document.class);
        method.setAccessible(true);

        Document doc = Jsoup.parse(
                "<html><body><a href='https://example.com/file.pdf'></a></body></html>",
                "https://example.com");

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, doc);

        String result = sb.toString();
        assertThat(result).contains("https://example.com/file.pdf");
    }

    // --- Additional coverage: appendLinksToContent with no valid links ---

    @Test
    void appendLinksToContentShouldHandleNoLinks() throws Exception {
        java.lang.reflect.Method method = TurWebCrawlerToolService.class.getDeclaredMethod(
                "appendLinksToContent", StringBuilder.class, Document.class);
        method.setAccessible(true);

        Document doc = Jsoup.parse("<html><body><p>No links here</p></body></html>",
                "https://example.com");

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, doc);

        String result = sb.toString();
        assertThat(result).contains("Links found on page");
        // No link entries like "- Page -> https://..."
        assertThat(result).doesNotContain("-> http");
    }

    @Test
    void appendLinksToContentShouldHandleOnlyJavascriptLinks() throws Exception {
        java.lang.reflect.Method method = TurWebCrawlerToolService.class.getDeclaredMethod(
                "appendLinksToContent", StringBuilder.class, Document.class);
        method.setAccessible(true);

        Document doc = Jsoup.parse(
                "<html><body>"
                + "<a href='javascript:void(0)'>Link1</a>"
                + "<a href='javascript:alert(1)'>Link2</a>"
                + "</body></html>",
                "https://example.com");

        StringBuilder sb = new StringBuilder();
        method.invoke(service, sb, doc);

        String result = sb.toString();
        assertThat(result).contains("Links found on page");
        assertThat(result).doesNotContain("Link1").doesNotContain("Link2");
    }

    // --- extractLinks with blank filter keyword ---

    @Test
    void extractLinksShouldReturnErrorForInvalidUrlWithBlankFilter() {
        String result = service.extractLinks("http://invalid-nonexistent-domain-99999.com", "   ");
        assertThat(result).contains("Error fetching URL");
    }

    // --- isValidExtractLink with empty filter keyword ---

    @Test
    void shouldAcceptExtractLinkWithEmptyFilterKeyword() throws Exception {
        Document doc = Jsoup.parse(
                "<a href='https://example.com/page'>Link</a>", "https://example.com");
        Element link = doc.select("a").first();

        // null filter accepts all valid links
        boolean result = (boolean) isValidExtractLinkMethod.invoke(service, link, null);
        assertThat(result).isTrue();
    }

    // --- isValidLink with various hrefs ---

    @Test
    void shouldAcceptRelativeLink() throws Exception {
        Document doc = Jsoup.parse("<a href='/path/to/page'>Link</a>", "https://example.com");
        Element link = doc.select("a").first();

        boolean result = (boolean) isValidLinkMethod.invoke(service, link);
        assertThat(result).isTrue();
    }

    @Test
    void shouldAcceptMailtoLink() throws Exception {
        Document doc = Jsoup.parse("<a href='mailto:test@example.com'>Email</a>", "https://example.com");
        Element link = doc.select("a").first();

        boolean result = (boolean) isValidLinkMethod.invoke(service, link);
        assertThat(result).isTrue();
    }

    // --- isValidExtractLink case-insensitive matching ---

    @Test
    void shouldMatchExtractLinkByTextKeywordCaseInsensitive() throws Exception {
        Document doc = Jsoup.parse(
                "<a href='https://example.com/page'>DOCUMENTATION Guide</a>", "https://example.com");
        Element link = doc.select("a").first();

        boolean result = (boolean) isValidExtractLinkMethod.invoke(service, link, "documentation");
        assertThat(result).isTrue();
    }

    @Test
    void shouldMatchExtractLinkByHrefKeywordCaseInsensitive() throws Exception {
        Document doc = Jsoup.parse(
                "<a href='https://example.com/API/v2'>Link</a>", "https://example.com");
        Element link = doc.select("a").first();

        boolean result = (boolean) isValidExtractLinkMethod.invoke(service, link, "api");
        assertThat(result).isTrue();
    }
}
