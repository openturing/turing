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
}
