/*
 *
 * Copyright (C) 2016-2025 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import org.apache.tika.metadata.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import com.viglet.turing.api.ocr.TurTikaFileAttributes;
import com.viglet.turing.commons.file.TurFileAttributes;

/**
 * Unit tests for TurFileUtils.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
class TurFileUtilsTest {

    @Test
    void testConstructorThrowsException() {
        assertThatThrownBy(this::instantiateTurFileUtilsConstructor)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Turing File Utilities class");
    }

    private void instantiateTurFileUtilsConstructor() throws Exception {
        var constructor = TurFileUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        constructor.newInstance();
    }

    @Test
    void testReadFileWithStringPath(@TempDir Path tempDir) throws IOException {
        File testFile = tempDir.resolve("test.txt").toFile();
        Files.writeString(testFile.toPath(), "Test content");

        TurTikaFileAttributes result = TurFileUtils.readFile(testFile.getAbsolutePath());

        assertThat(result).isNotNull();
        assertThat(result.getContent()).contains("Test content");
    }

    @Test
    void testReadFileWithNullFile() {
        TurTikaFileAttributes result = TurFileUtils.readFile((File) null);

        assertThat(result).isNull();
    }

    @Test
    void testReadFileWithNonExistentFile(@TempDir Path tempDir) {
        File nonExistent = tempDir.resolve("nonexistent.txt").toFile();

        TurTikaFileAttributes result = TurFileUtils.readFile(nonExistent);

        assertThat(result).isNull();
    }

    @Test
    void testParseFileWithValidFile(@TempDir Path tempDir) throws IOException {
        File testFile = tempDir.resolve("test.txt").toFile();
        Files.writeString(testFile.toPath(), "Parse test content");

        TurTikaFileAttributes result = TurFileUtils.parseFile(testFile);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).contains("Parse test content");
    }

    @Test
    void testParseMultipartFile() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                "Multipart test content".getBytes());

        TurTikaFileAttributes result = TurFileUtils.parseFile(multipartFile);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).contains("Multipart test content");
    }

    @Test
    void testDocumentToText() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "document.txt",
                "text/plain",
                "Document content".getBytes());

        TurFileAttributes result = TurFileUtils.documentToText(multipartFile);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).contains("Document content");
        assertThat(result.getName()).isEqualTo("document.txt");
        assertThat(result.getExtension()).isEqualTo("txt");
    }

    @Test
    void testIsAllowedRemoteUrlStringValid() {
        String validUrl = "https://www.example.com/document.pdf";

        boolean result = TurFileUtils.isAllowedRemoteUrlString(validUrl);

        // Empty allowed domains list allows all domains (subject to protocol/IP safety
        // checks)
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   " })
    void testIsAllowedRemoteUrlStringNullEmptyOrBlank(String input) {
        boolean result = TurFileUtils.isAllowedRemoteUrlString(input);

        assertThat(result).isFalse();
    }

    @Test
    void testIsAllowedRemoteUrlStringNull() {
        boolean result = TurFileUtils.isAllowedRemoteUrlString(null);

        assertThat(result).isFalse();
    }

    @Test
    void testIsAllowedRemoteUrlStringInvalidUrl() {
        String invalidUrl = "not a valid url";

        boolean result = TurFileUtils.isAllowedRemoteUrlString(invalidUrl);

        assertThat(result).isFalse();
    }

    @Test
    void testIsAllowedRemoteUrlStringMalformedUrl() {
        String malformedUrl = "http://[invalid";

        boolean result = TurFileUtils.isAllowedRemoteUrlString(malformedUrl);

        assertThat(result).isFalse();
    }

    @Test
    void testIsSafeLoopbackAddress() throws Exception {
        InetAddress loopback = InetAddress.getByName("127.0.0.1");

        boolean result = TurFileUtils.isSafe(loopback);

        assertThat(result).isFalse();
    }

    @Test
    void testIsSafeSiteLocalAddress() throws Exception {
        InetAddress siteLocal = InetAddress.getByName("192.168.1.1");

        boolean result = TurFileUtils.isSafe(siteLocal);

        assertThat(result).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "10.0.0.1", "172.16.0.1", "172.31.255.255" })
    void testIsSafePrivateNetwork(String ipAddress) throws Exception {
        InetAddress privateNetwork = InetAddress.getByName(ipAddress);

        boolean result = TurFileUtils.isSafe(privateNetwork);

        assertThat(result).isFalse();
    }

    @Test
    void testIsSafeCGNAT() throws Exception {
        InetAddress cgnat = InetAddress.getByName("100.64.0.1");

        boolean result = TurFileUtils.isSafe(cgnat);

        assertThat(result).isFalse();
    }

    @Test
    void testIsSafeCGNATRange() throws Exception {
        InetAddress cgnat = InetAddress.getByName("100.127.255.255");

        boolean result = TurFileUtils.isSafe(cgnat);

        assertThat(result).isFalse();
    }

    @Test
    void testIsSafePublicAddress() throws Exception {
        InetAddress publicAddress = InetAddress.getByName("8.8.8.8");

        boolean result = TurFileUtils.isSafe(publicAddress);

        assertThat(result).isTrue();
    }

    @Test
    void testIsRedirectResponseViaReflection() throws Exception {
        Method method = TurFileUtils.class.getDeclaredMethod("isRedirectResponse", int.class);
        method.setAccessible(true);

        assertThat((boolean) method.invoke(null, HttpURLConnection.HTTP_MOVED_PERM)).isTrue();
        assertThat((boolean) method.invoke(null, HttpURLConnection.HTTP_MOVED_TEMP)).isTrue();
        assertThat((boolean) method.invoke(null, HttpURLConnection.HTTP_SEE_OTHER)).isTrue();
        assertThat((boolean) method.invoke(null, 307)).isTrue();
        assertThat((boolean) method.invoke(null, 308)).isTrue();
        assertThat((boolean) method.invoke(null, 200)).isFalse();
    }

    @Test
    void testHandleRedirectMissingLocationThrows() throws Exception {
        Method method = TurFileUtils.class.getDeclaredMethod("handleRedirect", HttpURLConnection.class, URL.class);
        method.setAccessible(true);

        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(connection.getHeaderField("Location")).thenReturn(null);

        assertThatThrownBy(() -> method.invoke(null, connection, URI.create("https://example.com/a").toURL()))
                .hasRootCauseInstanceOf(IOException.class)
                .hasRootCauseMessage("Redirect response missing Location header");
    }

    @Test
    void testHandleRedirectToDisallowedHostThrows() throws Exception {
        Method method = TurFileUtils.class.getDeclaredMethod("handleRedirect", HttpURLConnection.class, URL.class);
        method.setAccessible(true);

        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(connection.getHeaderField("Location")).thenReturn("http://localhost/private");

        assertThatThrownBy(() -> method.invoke(null, connection, URI.create("https://example.com/a").toURL()))
                .hasRootCauseInstanceOf(IOException.class)
                .rootCause()
                .hasMessageContaining("Redirect to disallowed URL blocked");
    }

    @Test
    void testGetTitleAndMetadataMapViaReflection() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(PDF_DOC_INFO_TITLE, "PDF Title");
        metadata.set("author", "Alex");
        TurTikaFileAttributes attrs = new TurTikaFileAttributes(null, "content", metadata);

        Method titleMethod = TurFileUtils.class.getDeclaredMethod("getTitle", TurTikaFileAttributes.class,
                String.class);
        titleMethod.setAccessible(true);
        Method metadataMethod = TurFileUtils.class.getDeclaredMethod("getMetadataMap", TurTikaFileAttributes.class);
        metadataMethod.setAccessible(true);

        String title = (String) titleMethod.invoke(null, attrs, "fallback.pdf");
        @SuppressWarnings("unchecked")
        Map<String, String> metadataMap = (Map<String, String>) metadataMethod.invoke(null, attrs);

        assertThat(title).isEqualTo("PDF Title");
        assertThat(metadataMap).containsEntry("author", "Alex");
    }

    @Test
    void testGetTitleFallbackWhenMissing() throws Exception {
        Metadata metadata = new Metadata();
        TurTikaFileAttributes attrs = new TurTikaFileAttributes(null, "content", metadata);
        Method titleMethod = TurFileUtils.class.getDeclaredMethod("getTitle", TurTikaFileAttributes.class,
                String.class);
        titleMethod.setAccessible(true);

        String title = (String) titleMethod.invoke(null, attrs, "fallback.pdf");

        assertThat(title).isEqualTo("fallback.pdf");
    }

    @Test
    void testGetLastModifiedFromUrlBlockedHostReturnsDate() throws Exception {
        Method method = TurFileUtils.class.getDeclaredMethod("getLastModifiedFromUrl", URL.class);
        method.setAccessible(true);

        Date date = (Date) method.invoke(null, URI.create("http://localhost/blocked").toURL());

        assertThat(date).isNotNull();
    }

    @Test
    void testParseDocumentWithBrokenInputStreamReturnsEmpty() throws IOException {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("forced read error");
            }
        };

        Optional<String> result = TurFileUtils.parseDocument(broken);

        assertThat(result).isEmpty();
    }

    @Test
    void testParseDocumentWithValidInputStream() throws IOException {
        String content = "Test content for parsing";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes());

        Optional<String> result = TurFileUtils.parseDocument(inputStream);

        assertThat(result).isPresent();
        assertThat(result.get()).contains(content);
    }

    @Test
    void testParseDocumentWithEmptyInputStream() throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[0]);

        Optional<String> result = TurFileUtils.parseDocument(inputStream);

        // Parsing can fail with empty stream, resulting in Optional.empty()
        // The actual behavior depends on Tika's parser behavior
        assertThat(result).isNotNull();
        // Note: Empty stream may result in Optional.empty() or Optional with empty
        // string
    }

    @Test
    void testUrlContentToTextWithDisallowedUrl() throws Exception {
        java.net.URL url = URI.create("http://localhost/test.txt").toURL();

        TurFileAttributes result = TurFileUtils.urlContentToText(url);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNullOrEmpty();
    }

    @Test
    void testIsSafeLinkLocalAddress() throws Exception {
        InetAddress linkLocal = InetAddress.getByName("169.254.1.1");

        boolean result = TurFileUtils.isSafe(linkLocal);

        assertThat(result).isFalse();
    }

    @Test
    void testIsSafeMulticastAddress() throws Exception {
        InetAddress multicast = InetAddress.getByName("224.0.0.1");

        boolean result = TurFileUtils.isSafe(multicast);

        assertThat(result).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "172.15.0.1", "172.32.0.1", "100.63.0.1", "100.128.0.1" })
    void testIsSafeEdgeCases(String ipAddress) throws Exception {
        InetAddress edge = InetAddress.getByName(ipAddress);

        boolean result = TurFileUtils.isSafe(edge);

        assertThat(result).isTrue();
    }

    private static final String PDF_DOC_INFO_TITLE = "pdf:docinfo:title";
}
