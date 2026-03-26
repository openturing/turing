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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Tests for TurFileUtils.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
class TurFileUtilsTest {

    private static final String PDF_DOC_INFO_TITLE = "pdf:docinfo:title";

    @Test
    void testConstructorThrowsException() throws NoSuchMethodException {
        var constructor = TurFileUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);

        assertThatThrownBy(constructor::newInstance)
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Turing File Utilities class");
    }

    // --- readFile(String) ---

    @Test
    void testReadFileWithStringPath(@TempDir Path tempDir) throws IOException {
        File testFile = tempDir.resolve("test.txt").toFile();
        Files.writeString(testFile.toPath(), "Test content");

        TurTikaFileAttributes result = TurFileUtils.readFile(testFile.getAbsolutePath());

        assertThat(result).isNotNull();
        assertThat(result.getContent()).contains("Test content");
    }

    @Test
    void testReadFileWithStringPathNonExistent() {
        TurTikaFileAttributes result = TurFileUtils.readFile("/nonexistent/path/file.txt");

        assertThat(result).isNull();
    }

    // --- readFile(File) ---

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
    void testReadFileWithValidTextFile(@TempDir Path tempDir) throws IOException {
        File testFile = tempDir.resolve("valid.txt").toFile();
        Files.writeString(testFile.toPath(), "Valid content here");

        TurTikaFileAttributes result = TurFileUtils.readFile(testFile);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).contains("Valid content here");
    }

    @Test
    void testReadFileWithEmptyFile(@TempDir Path tempDir) throws IOException {
        File emptyFile = tempDir.resolve("empty.txt").toFile();
        Files.writeString(emptyFile.toPath(), "");

        TurTikaFileAttributes result = TurFileUtils.readFile(emptyFile);

        assertThat(result).isNotNull();
    }

    // --- parseFile(File) ---

    @Test
    void testParseFileWithValidFile(@TempDir Path tempDir) throws IOException {
        File testFile = tempDir.resolve("test.txt").toFile();
        Files.writeString(testFile.toPath(), "Parse test content");

        TurTikaFileAttributes result = TurFileUtils.parseFile(testFile);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).contains("Parse test content");
    }

    @Test
    void testParseFileWithHtmlContent(@TempDir Path tempDir) throws IOException {
        File htmlFile = tempDir.resolve("test.html").toFile();
        Files.writeString(htmlFile.toPath(), "<html><body><p>HTML content</p></body></html>");

        TurTikaFileAttributes result = TurFileUtils.parseFile(htmlFile);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).contains("HTML content");
    }

    // --- parseFile(MultipartFile) ---

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
    void testParseMultipartFileWithEmptyContent() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]);

        TurTikaFileAttributes result = TurFileUtils.parseFile(multipartFile);

        assertThat(result).isNotNull();
    }

    // --- documentToText ---

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
    void testDocumentToTextWithNullFilename() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                null,
                "text/plain",
                "content".getBytes());

        TurFileAttributes result = TurFileUtils.documentToText(multipartFile);

        assertThat(result).isNotNull();
    }

    @Test
    void testDocumentToTextWithNoExtension() {
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "noext",
                "text/plain",
                "content".getBytes());

        TurFileAttributes result = TurFileUtils.documentToText(multipartFile);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("noext");
        assertThat(result.getExtension()).isEmpty();
    }

    @Test
    void testDocumentToTextSize() {
        byte[] content = "A".repeat(500).getBytes();
        MockMultipartFile multipartFile = new MockMultipartFile(
                "file",
                "sized.txt",
                "text/plain",
                content);

        TurFileAttributes result = TurFileUtils.documentToText(multipartFile);

        assertThat(result).isNotNull();
        assertThat(result.getSize()).isNotNull();
    }

    // --- isAllowedRemoteUrlString ---

    @Test
    void testIsAllowedRemoteUrlStringValid() {
        boolean result = TurFileUtils.isAllowedRemoteUrlString("https://www.example.com/document.pdf");

        assertThat(result).isTrue();
    }

    @Test
    void testIsAllowedRemoteUrlStringNull() {
        assertThat(TurFileUtils.isAllowedRemoteUrlString(null)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "\t", "\n" })
    void testIsAllowedRemoteUrlStringBlankInputs(String input) {
        assertThat(TurFileUtils.isAllowedRemoteUrlString(input)).isFalse();
    }

    @Test
    void testIsAllowedRemoteUrlStringInvalidUrl() {
        assertThat(TurFileUtils.isAllowedRemoteUrlString("not a valid url")).isFalse();
    }

    @Test
    void testIsAllowedRemoteUrlStringMalformedUrl() {
        assertThat(TurFileUtils.isAllowedRemoteUrlString("http://[invalid")).isFalse();
    }

    @Test
    void testIsAllowedRemoteUrlStringFtpProtocol() {
        assertThat(TurFileUtils.isAllowedRemoteUrlString("ftp://example.com/file")).isFalse();
    }

    @Test
    void testIsAllowedRemoteUrlStringFileProtocol() {
        assertThat(TurFileUtils.isAllowedRemoteUrlString("file:///etc/passwd")).isFalse();
    }

    @Test
    void testIsAllowedRemoteUrlStringLocalhostBlocked() {
        assertThat(TurFileUtils.isAllowedRemoteUrlString("http://localhost/test")).isFalse();
    }

    @Test
    void testIsAllowedRemoteUrlStringLoopbackBlocked() {
        assertThat(TurFileUtils.isAllowedRemoteUrlString("http://127.0.0.1/test")).isFalse();
    }

    @Test
    void testIsAllowedRemoteUrlStringPrivateNetworkBlocked() {
        assertThat(TurFileUtils.isAllowedRemoteUrlString("http://192.168.1.1/test")).isFalse();
    }

    // --- isSafe ---

    @Test
    void testIsSafeLoopbackAddress() throws Exception {
        assertThat(TurFileUtils.isSafe(InetAddress.getByName("127.0.0.1"))).isFalse();
    }

    @Test
    void testIsSafeSiteLocalAddress() throws Exception {
        assertThat(TurFileUtils.isSafe(InetAddress.getByName("192.168.1.1"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "10.0.0.1", "10.255.255.255", "172.16.0.1", "172.31.255.255" })
    void testIsSafePrivateNetworkRanges(String ipAddress) throws Exception {
        assertThat(TurFileUtils.isSafe(InetAddress.getByName(ipAddress))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "100.64.0.1", "100.100.0.1", "100.127.255.255" })
    void testIsSafeCGNATRange(String ipAddress) throws Exception {
        assertThat(TurFileUtils.isSafe(InetAddress.getByName(ipAddress))).isFalse();
    }

    @Test
    void testIsSafeLinkLocalAddress() throws Exception {
        assertThat(TurFileUtils.isSafe(InetAddress.getByName("169.254.1.1"))).isFalse();
    }

    @Test
    void testIsSafeMulticastAddress() throws Exception {
        assertThat(TurFileUtils.isSafe(InetAddress.getByName("224.0.0.1"))).isFalse();
    }

    @Test
    void testIsSafePublicAddress() throws Exception {
        assertThat(TurFileUtils.isSafe(InetAddress.getByName("8.8.8.8"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = { "172.15.0.1", "172.32.0.1", "100.63.0.1", "100.128.0.1", "11.0.0.1" })
    void testIsSafeEdgeCasesOutsidePrivateRange(String ipAddress) throws Exception {
        assertThat(TurFileUtils.isSafe(InetAddress.getByName(ipAddress))).isTrue();
    }

    @Test
    void testIsSafe192168Boundary() throws Exception {
        // 192.168.x.x is private, but 192.169.x.x is public
        assertThat(TurFileUtils.isSafe(InetAddress.getByName("192.168.0.1"))).isFalse();
        assertThat(TurFileUtils.isSafe(InetAddress.getByName("192.169.0.1"))).isTrue();
    }

    // --- isRedirectResponse (via reflection) ---

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
        assertThat((boolean) method.invoke(null, 404)).isFalse();
        assertThat((boolean) method.invoke(null, 500)).isFalse();
        assertThat((boolean) method.invoke(null, 201)).isFalse();
    }

    // --- handleRedirect (via reflection) ---

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
    void testHandleRedirectEmptyLocationThrows() throws Exception {
        Method method = TurFileUtils.class.getDeclaredMethod("handleRedirect", HttpURLConnection.class, URL.class);
        method.setAccessible(true);

        HttpURLConnection connection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(connection.getHeaderField("Location")).thenReturn("");

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

    // --- getTitle / getMetadataMap (via reflection) ---

    @Test
    void testGetTitleFromPdfMetadata() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(PDF_DOC_INFO_TITLE, "PDF Title");
        metadata.set("author", "Alex");
        TurTikaFileAttributes attrs = new TurTikaFileAttributes(null, "content", metadata);

        Method titleMethod = TurFileUtils.class.getDeclaredMethod("getTitle", TurTikaFileAttributes.class,
                String.class);
        titleMethod.setAccessible(true);

        String title = (String) titleMethod.invoke(null, attrs, "fallback.pdf");

        assertThat(title).isEqualTo("PDF Title");
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
    void testGetTitleFallbackWhenBlank() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(PDF_DOC_INFO_TITLE, "   ");
        TurTikaFileAttributes attrs = new TurTikaFileAttributes(null, "content", metadata);

        Method titleMethod = TurFileUtils.class.getDeclaredMethod("getTitle", TurTikaFileAttributes.class,
                String.class);
        titleMethod.setAccessible(true);

        String title = (String) titleMethod.invoke(null, attrs, "fallback.pdf");

        assertThat(title).isEqualTo("fallback.pdf");
    }

    @Test
    void testGetMetadataMapReturnsAllEntries() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set("author", "Alex");
        metadata.set("title", "Doc Title");
        metadata.set("keywords", "java, test");
        TurTikaFileAttributes attrs = new TurTikaFileAttributes(null, "content", metadata);

        Method metadataMethod = TurFileUtils.class.getDeclaredMethod("getMetadataMap", TurTikaFileAttributes.class);
        metadataMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> metadataMap = (Map<String, String>) metadataMethod.invoke(null, attrs);

        assertThat(metadataMap).containsEntry("author", "Alex");
        assertThat(metadataMap).containsEntry("title", "Doc Title");
        assertThat(metadataMap).containsEntry("keywords", "java, test");
        assertThat(metadataMap).hasSize(3);
    }

    @Test
    void testGetMetadataMapEmptyMetadata() throws Exception {
        Metadata metadata = new Metadata();
        TurTikaFileAttributes attrs = new TurTikaFileAttributes(null, "content", metadata);

        Method metadataMethod = TurFileUtils.class.getDeclaredMethod("getMetadataMap", TurTikaFileAttributes.class);
        metadataMethod.setAccessible(true);

        @SuppressWarnings("unchecked")
        Map<String, String> metadataMap = (Map<String, String>) metadataMethod.invoke(null, attrs);

        assertThat(metadataMap).isEmpty();
    }

    // --- getLastModifiedFromUrl ---

    @Test
    void testGetLastModifiedFromUrlBlockedHostReturnsDate() throws Exception {
        Method method = TurFileUtils.class.getDeclaredMethod("getLastModifiedFromUrl", URL.class);
        method.setAccessible(true);

        java.util.Date date = (java.util.Date) method.invoke(null,
                URI.create("http://localhost/blocked").toURL());

        assertThat(date).isNotNull();
    }

    // --- urlContentToText ---

    @Test
    void testUrlContentToTextWithDisallowedUrl() throws Exception {
        URL url = URI.create("http://localhost/test.txt").toURL();

        TurFileAttributes result = TurFileUtils.urlContentToText(url);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNullOrEmpty();
    }

    @Test
    void testUrlContentToTextWithPrivateNetworkUrl() throws Exception {
        URL url = URI.create("http://10.0.0.1/test.txt").toURL();

        TurFileAttributes result = TurFileUtils.urlContentToText(url);

        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNullOrEmpty();
    }

    // --- parseDocument ---

    @Test
    void testParseDocumentWithValidInputStream() throws IOException {
        String content = "Test content for parsing";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes());

        Optional<String> result = TurFileUtils.parseDocument(inputStream);

        assertThat(result).isPresent();
        assertThat(result.get()).contains(content);
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
    void testParseDocumentWithEmptyInputStream() throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(new byte[0]);

        Optional<String> result = TurFileUtils.parseDocument(inputStream);

        assertThat(result).isNotNull();
    }

    @Test
    void testParseDocumentWithHtmlContent() throws IOException {
        String html = "<html><body><p>Paragraph content</p></body></html>";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(html.getBytes());

        Optional<String> result = TurFileUtils.parseDocument(inputStream);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Paragraph content");
    }

    @Test
    void testParseDocumentWithMultilineContent() throws IOException {
        String content = "Line one\nLine two\nLine three";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(content.getBytes());

        Optional<String> result = TurFileUtils.parseDocument(inputStream);

        assertThat(result).isPresent();
        assertThat(result.get()).contains("Line one");
        assertThat(result.get()).contains("Line three");
    }
}
