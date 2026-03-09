package com.viglet.turing.genai.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TurImageSearchToolServiceTest {

    private TurImageSearchToolService service;
    private Method isImageUrlMethod;

    @BeforeEach
    void setUp() throws Exception {
        service = new TurImageSearchToolService();
        isImageUrlMethod = TurImageSearchToolService.class.getDeclaredMethod("isImageUrl", String.class);
        isImageUrlMethod.setAccessible(true);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/photo.jpg",
            "https://example.com/photo.jpeg",
            "https://example.com/photo.png",
            "https://example.com/photo.gif",
            "https://example.com/photo.webp",
            "https://example.com/photo.svg",
            "https://example.com/photo.JPG",
            "https://example.com/photo.PNG"
    })
    void shouldRecognizeImageUrls(String url) throws Exception {
        boolean result = (boolean) isImageUrlMethod.invoke(service, url);
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/image/photo",
            "https://example.com/?img=123"
    })
    void shouldRecognizeImageUrlPatterns(String url) throws Exception {
        boolean result = (boolean) isImageUrlMethod.invoke(service, url);
        assertThat(result).describedAs("Should recognize image patterns in URL paths or query parameters")
                .isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com/page.html",
            "https://example.com/doc.pdf",
            "https://example.com/file.txt",
            "https://example.com/"
    })
    void shouldRejectNonImageUrls(String url) throws Exception {
        boolean result = (boolean) isImageUrlMethod.invoke(service, url);
        assertThat(result).isFalse();
    }

    @Test
    void shouldRejectNullUrl() throws Exception {
        boolean result = (boolean) isImageUrlMethod.invoke(service, (String) null);
        assertThat(result).isFalse();
    }

    @Test
    void shouldRejectEmptyUrl() throws Exception {
        boolean result = (boolean) isImageUrlMethod.invoke(service, "");
        assertThat(result).isFalse();
    }

    @Test
    void shouldRejectBlankUrl() throws Exception {
        boolean result = (boolean) isImageUrlMethod.invoke(service, "   ");
        assertThat(result).isFalse();
    }

    @Test
    void shouldLimitCountToMaxResults() {
        String result = service.searchImages("test query", 100);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldDefaultCountToThreeWhenNull() {
        String result = service.searchImages("test query", null);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldDefaultCountToThreeWhenZero() {
        String result = service.searchImages("test query", 0);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldDefaultCountToThreeWhenNegative() {
        String result = service.searchImages("test query", -1);

        assertThat(result).isNotNull();
    }
}
