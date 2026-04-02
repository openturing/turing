package com.viglet.turing.service.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for TurStorageContentTypes.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
class TurStorageContentTypesTest {

    @Test
    void shouldReturnPdfForPdfExtension() {
        assertThat(TurStorageContentTypes.guessContentType("document.pdf")).isEqualTo("application/pdf");
    }

    @Test
    void shouldReturnPngForPngExtension() {
        assertThat(TurStorageContentTypes.guessContentType("image.png")).isEqualTo("image/png");
    }

    @Test
    void shouldReturnJpegForJpgExtension() {
        assertThat(TurStorageContentTypes.guessContentType("photo.jpg")).isEqualTo("image/jpeg");
        assertThat(TurStorageContentTypes.guessContentType("photo.jpeg")).isEqualTo("image/jpeg");
    }

    @Test
    void shouldReturnOctetStreamForUnknown() {
        assertThat(TurStorageContentTypes.guessContentType("unknown.xyz")).isEqualTo("application/octet-stream");
    }

    @Test
    void shouldReturnOctetStreamForNull() {
        assertThat(TurStorageContentTypes.guessContentType(null)).isEqualTo("application/octet-stream");
    }

    @Test
    void shouldBeCaseInsensitive() {
        assertThat(TurStorageContentTypes.guessContentType("IMAGE.PNG")).isEqualTo("image/png");
        assertThat(TurStorageContentTypes.guessContentType("Document.PDF")).isEqualTo("application/pdf");
    }

    @Test
    void shouldReturnOctetStreamForEmptyString() {
        assertThat(TurStorageContentTypes.guessContentType("")).isEqualTo("application/octet-stream");
    }

    @Test
    void shouldReturnOctetStreamForNoExtension() {
        assertThat(TurStorageContentTypes.guessContentType("filenoext")).isEqualTo("application/octet-stream");
    }

    @Test
    void shouldReturnHtmlForHtmlExtension() {
        assertThat(TurStorageContentTypes.guessContentType("page.html")).isEqualTo("text/html");
        assertThat(TurStorageContentTypes.guessContentType("page.htm")).isEqualTo("text/html");
    }

    @Test
    void shouldReturnJsonForJsonExtension() {
        assertThat(TurStorageContentTypes.guessContentType("config.json")).isEqualTo("application/json");
    }

    @Test
    void shouldReturnZipForZipExtension() {
        assertThat(TurStorageContentTypes.guessContentType("archive.zip")).isEqualTo("application/zip");
    }
}
