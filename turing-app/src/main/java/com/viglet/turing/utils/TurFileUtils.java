/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.io.FilenameUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ocr.TesseractOCRConfig;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.viglet.turing.api.ocr.TurTikaFileAttributes;
import com.viglet.turing.commons.file.TurFileAttributes;
import com.viglet.turing.commons.file.TurFileSize;
import com.viglet.turing.commons.utils.TurCommonsUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TurFileUtils {

    private static final Set<String> ALLOWED_DOMAINS = Set.of(
    // Add allowed domains below, e.g.:
    // "example.com",
    // "sometrustedsource.org"
    );

    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("http", "https");
    private static final int CONNECTION_TIMEOUT_MILLIS = 5000;
    private static final int MAX_REDIRECTS = 5;
    private static final String PDF_DOC_INFO_TITLE = "pdf:docinfo:title";
    private static final String TMP = "tmp";

    private TurFileUtils() {
        throw new IllegalStateException("Turing File Utilities class");
    }

    public static TurTikaFileAttributes readFile(String filePath) {
        return readFile(new File(filePath));
    }

    public static TurTikaFileAttributes readFile(File file) {
        if (file == null || !file.exists()) {
            log.info("File not exists: {}", file != null ? file.getAbsolutePath() : "null");
            return null;
        }
        return parseFile(file);
    }

    public static TurTikaFileAttributes parseFile(File file) {
        try (InputStream inputStream = new FileInputStream(file)) {
            return getTurTikaFileAttributes(file, inputStream);
        } catch (IOException e) {
            log.error("Error parsing file: {}", e.getMessage(), e);
            return null;
        }
    }

    public static TurTikaFileAttributes parseFile(MultipartFile multipartFile) {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            return getTurTikaFileAttributes(null, inputStream);
        } catch (IOException e) {
            log.error("Error parsing multipart file: {}", e.getMessage(), e);
            return null;
        }
    }

    private static TurTikaFileAttributes getTurTikaFileAttributes(File file, InputStream inputStream) {
        StringBuilder contentFile = new StringBuilder();
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();

        EmbeddedDocumentExtractor embeddedDocumentExtractor = createEmbeddedDocumentExtractor(contentFile);
        ParseContext parseContext = createParseContext(parser);
        parseContext.set(EmbeddedDocumentExtractor.class, embeddedDocumentExtractor);

        try {
            parser.parse(inputStream, handler, metadata, parseContext);
        } catch (IOException | SAXException | TikaException e) {
            log.error("Error during Tika parsing: {}", e.getMessage(), e);
        }

        contentFile.append(handler);
        return new TurTikaFileAttributes(file, contentFile.toString(), metadata);
    }

    private static EmbeddedDocumentExtractor createEmbeddedDocumentExtractor(StringBuilder contentFile) {
        return new EmbeddedDocumentExtractor() {
            @Override
            public boolean shouldParseEmbedded(Metadata metadata) {
                return true;
            }

            @Override
            public void parseEmbedded(InputStream stream, ContentHandler handler, Metadata metadata,
                    boolean outputHtml) throws IOException {
                parseDocument(stream).ifPresent(contentFile::append);
            }
        };
    }

    private static ParseContext createParseContext(AutoDetectParser parser) {
        TesseractOCRConfig config = new TesseractOCRConfig();
        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(true);

        ParseContext parseContext = new ParseContext();
        parseContext.set(TesseractOCRConfig.class, config);
        parseContext.set(PDFParserConfig.class, pdfConfig);
        parseContext.set(Parser.class, parser);
        return parseContext;
    }

    public static TurFileAttributes documentToText(MultipartFile multipartFile) {
        TurTikaFileAttributes tikaFileAttributes = parseFile(multipartFile);
        if (tikaFileAttributes == null) {
            return new TurFileAttributes();
        }

        return buildTurFileAttributes(
                tikaFileAttributes,
                multipartFile.getOriginalFilename(),
                FilenameUtils.getExtension(multipartFile.getOriginalFilename()),
                multipartFile.getSize(),
                getTikaLastModified(tikaFileAttributes).orElseGet(Date::new));
    }

    public static TurFileAttributes urlContentToText(URL url) {
        if (!isAllowedRemoteUrl(url)) {
            log.warn("Blocked attempt to access disallowed URL: {}", url);
            return new TurFileAttributes();
        }

        log.info("Processing {} document to text", url);

        return fetchAndParseUrl(url)
                .map(result -> buildTurFileAttributes(
                        result.tikaFileAttributes,
                        FilenameUtils.getName(url.getPath()),
                        FilenameUtils.getExtension(url.getPath()),
                        result.fileSize,
                        getLastModified(result.tikaFileAttributes, url)))
                .orElseGet(TurFileAttributes::new);
    }

    /**
     * Helper method to validate a URL provided as a String before it is parsed and
     * used.
     *
     * @param urlString the URL string supplied by a client
     * @return true if the URL is syntactically valid and allowed according to
     *         isAllowedRemoteUrl(URL)
     */
    public static boolean isAllowedRemoteUrlString(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            return false;
        }
        try {
            URL url = URI.create(urlString).toURL();
            return isAllowedRemoteUrl(url);
        } catch (IllegalArgumentException | MalformedURLException e) {
            log.warn("Invalid or malformed URL string received: {}", urlString);
            return false;
        }
    }

    private static Optional<UrlParseResult> fetchAndParseUrl(URL url) {
        File tempFile;
        try {
            tempFile = createTempFile();
        } catch (IOException e) {
            log.error("Error creating temp file: {}", e.getMessage(), e);
            return Optional.empty();
        }
        try {
            copyURLToFileSafe(url, tempFile);
            TurTikaFileAttributes tikaFileAttributes = parseFile(tempFile);
            return Optional.ofNullable(tikaFileAttributes)
                    .map(attrs -> new UrlParseResult(attrs, tempFile.length()));
        } catch (IOException e) {
            log.error("Error fetching URL content: {}", e.getMessage(), e);
            return Optional.empty();
        } finally {
            tempFile.deleteOnExit();
        }
    }

    private record UrlParseResult(TurTikaFileAttributes tikaFileAttributes, long fileSize) {
    }

    private static TurFileAttributes buildTurFileAttributes(TurTikaFileAttributes tikaFileAttributes,
            String fileName, String fileExtension, long fileSize, Date lastModified) {
        return TurFileAttributes.builder()
                .content(tikaFileAttributes.getContent())
                .name(fileName)
                .extension(fileExtension)
                .size(new TurFileSize(fileSize))
                .title(getTitle(tikaFileAttributes, fileName))
                .lastModified(lastModified)
                .metadata(getMetadataMap(tikaFileAttributes))
                .build();
    }

    private static boolean isAllowedRemoteUrl(URL url) {
        if (url == null) {
            return false;
        }

        String host = url.getHost();
        String protocol = url.getProtocol();

        if (!StringUtils.hasText(host) || !StringUtils.hasText(protocol)) {
            return false;
        }

        if (!ALLOWED_PROTOCOLS.contains(protocol.toLowerCase())) {
            return false;
        }

        try {
            InetAddress inetAddress = InetAddress.getByName(host);
            if (!isSafe(inetAddress)) {
                return false;
            }
        } catch (UnknownHostException e) {
            log.warn("Unknown host in user-supplied URL: {}", url);
            return false;
        }

        return ALLOWED_DOMAINS.isEmpty() || isHostInAllowedDomains(host);
    }

    private static boolean isHostInAllowedDomains(String host) {
        return ALLOWED_DOMAINS.stream()
                .anyMatch(allowed -> host.equalsIgnoreCase(allowed) || host.endsWith("." + allowed));
    }

    private static void copyURLToFileSafe(URL url, File destination) throws IOException {
        HttpURLConnection connection = null;
        URL currentUrl = url;

        try {
            for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
                connection = createConnection(currentUrl);
                if (connection == null) {
                    throw new IOException("Connection blocked for unsafe or disallowed URL: " + currentUrl);
                }
                int responseCode = connection.getResponseCode();

                if (isRedirectResponse(responseCode)) {
                    currentUrl = handleRedirect(connection, currentUrl);
                    connection.disconnect();
                    continue;
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("Failed to fetch URL, response code: " + responseCode);
                }

                try (InputStream inputStream = connection.getInputStream()) {
                    Files.copy(inputStream, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            }
            throw new IOException("Too many redirects");
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static HttpURLConnection createConnection(URL url) throws IOException {
        String originalHost = url.getHost();

        if (!ALLOWED_DOMAINS.isEmpty() && !isHostInAllowedDomains(originalHost)) {
            log.warn("Host not in allowed domains: {}", originalHost);
            return null;
        }

        InetAddress resolvedAddress = InetAddress.getByName(originalHost);
        if (!isSafe(resolvedAddress)) {
            log.warn("Resolved address is not safe: {} -> {}", originalHost, resolvedAddress.getHostAddress());
            return null;
        }

        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECTION_TIMEOUT_MILLIS);
            connection.setReadTimeout(CONNECTION_TIMEOUT_MILLIS);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Host", originalHost);

            return connection;
        } catch (IOException e) {
            throw new IOException("Failed to create connection to: " + originalHost, e);
        }
    }

    public static boolean isSafe(InetAddress address) {
        if (address.isLoopbackAddress() ||
                address.isSiteLocalAddress() ||
                address.isLinkLocalAddress() ||
                address.isAnyLocalAddress() ||
                address.isMulticastAddress()) {
            return false;
        }

        return isSafeInet4Address(address);
    }

    private static boolean isSafeInet4Address(InetAddress address) {
        if (address instanceof Inet4Address) {
            byte[] addr = address.getAddress();
            int firstOctet = addr[0] & 0xFF;
            int secondOctet = addr[1] & 0xFF;

            // 10.0.0.0/8
            if (firstOctet == 10)
                return false;

            // 172.16.0.0/12
            if (firstOctet == 172 && (secondOctet >= 16 && secondOctet <= 31))
                return false;

            // 192.168.0.0/16
            if (firstOctet == 192 && secondOctet == 168)
                return false;

            // 100.64.0.0/10 (CGNAT)
            if (firstOctet == 100 && (secondOctet >= 64 && secondOctet <= 127))
                return false;
        }
        return true;
    }

    private static boolean isRedirectResponse(int responseCode) {
        return responseCode == HttpURLConnection.HTTP_MOVED_PERM
                || responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                || responseCode == HttpURLConnection.HTTP_SEE_OTHER
                || responseCode == 307
                || responseCode == 308;
    }

    private static URL handleRedirect(HttpURLConnection connection, URL currentUrl) throws IOException {
        String redirectLocation = connection.getHeaderField("Location");
        if (redirectLocation == null || redirectLocation.isEmpty()) {
            throw new IOException("Redirect response missing Location header");
        }

        try {
            URL redirectUrl = currentUrl.toURI().resolve(redirectLocation).toURL();
            if (!isAllowedRemoteUrl(redirectUrl)) {
                throw new IOException("Redirect to disallowed URL blocked: " + redirectUrl);
            }
            return redirectUrl;
        } catch (URISyntaxException e) {
            throw new IOException("Invalid redirect URL: " + redirectLocation, e);
        }
    }

    private static Date getLastModified(TurTikaFileAttributes tikaFileAttributes, URL url) {
        return getTikaLastModified(tikaFileAttributes).orElseGet(() -> getLastModifiedFromUrl(url));
    }

    private static Optional<Date> getTikaLastModified(TurTikaFileAttributes tikaFileAttributes) {
        return Optional.ofNullable(tikaFileAttributes)
                .flatMap(t -> Optional.ofNullable(t.getMetadata())
                        .map(m -> m.getDate(DublinCore.MODIFIED)));
    }

    private static Date getLastModifiedFromUrl(URL url) {
        if (!isAllowedRemoteUrl(url)) {
            log.warn("Blocked attempt to get last modified from disallowed URL: {}", url);
            return new Date();
        }

        HttpURLConnection connection = null;
        try {
            connection = createConnection(url);
            connection.setRequestMethod("HEAD");
            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return new Date(connection.getLastModified());
            }
            log.warn("Unexpected response code {} from URL: {}", connection.getResponseCode(), url);
        } catch (IOException e) {
            log.error("Error getting last modified from URL: {}", e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return new Date();
    }

    private static String getTitle(TurTikaFileAttributes tikaFileAttributes, String fileName) {
        return Optional.ofNullable(tikaFileAttributes.getMetadata().get(PDF_DOC_INFO_TITLE))
                .filter(title -> !title.isBlank())
                .orElse(fileName);
    }

    private static Map<String, String> getMetadataMap(TurTikaFileAttributes file) {
        Map<String, String> metadataMap = new HashMap<>();
        Arrays.stream(file.getMetadata().names())
                .forEach(name -> metadataMap.put(name, file.getMetadata().get(name)));
        return metadataMap;
    }

    public static Optional<String> parseDocument(InputStream stream) throws IOException {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        ParseContext parseContext = createParseContext(parser);

        File tempFile = createTempFile();
        try {
            Files.copy(stream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            try (FileInputStream fileInputStream = new FileInputStream(tempFile)) {
                parser.parse(fileInputStream, handler, metadata, parseContext);
                return Optional.of(handler.toString());
            }
        } catch (IOException | SAXException | TikaException e) {
            log.error("Error parsing document: {}", e.getMessage(), e);
            return Optional.empty();
        } finally {
            tempFile.deleteOnExit();
        }
    }

    private static File createTempFile() throws IOException {
        return File.createTempFile(UUID.randomUUID().toString(), null,
                TurCommonsUtils.addSubDirToStoreDir(TMP));
    }
}
