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
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
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
import org.apache.tika.metadata.DublinCore;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.core.content.VigletContentExtractor;
import com.viglet.core.content.VigletExtractedContent;
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

    // Many sites sit behind a CDN/WAF (nginx, Akamai, Cloudflare) that rejects
    // the default Java HttpURLConnection User-Agent ("Java/21") with a bot-block
    // response such as HTTP 444 (nginx "no response"). Present browser-like
    // request headers so legitimate content ingestion isn't refused.
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,"
            + "application/pdf,*/*;q=0.8";
    private static final String ACCEPT_LANGUAGE = "en-US,en;q=0.9";

    private TurFileUtils() {
        throw new IllegalStateException("Turing File Utilities class");
    }

    public static TurTikaFileAttributes readFile(String filePath) {
        return readFile(new File(filePath));
    }

    public static TurTikaFileAttributes readFile(File file) {
        return toTikaAttributes(VigletContentExtractor.extract(file));
    }

    public static TurTikaFileAttributes parseFile(File file) {
        return toTikaAttributes(VigletContentExtractor.extract(file));
    }

    public static TurTikaFileAttributes parseFile(MultipartFile multipartFile) {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            return toTikaAttributes(VigletContentExtractor.extract(inputStream));
        } catch (IOException e) {
            log.error("Error parsing multipart file: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Adapts the neutral {@link VigletExtractedContent} from
     * {@link VigletContentExtractor} (Block Q / T374) into Turing's
     * {@link TurTikaFileAttributes}. Returns {@code null} when extraction failed
     * or the source file was missing.
     */
    private static TurTikaFileAttributes toTikaAttributes(VigletExtractedContent content) {
        return content == null ? null
                : new TurTikaFileAttributes(content.file(), content.content(), content.metadata());
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
            connection.setRequestProperty("User-Agent", USER_AGENT);
            connection.setRequestProperty("Accept", ACCEPT);
            connection.setRequestProperty("Accept-Language", ACCEPT_LANGUAGE);

            return connection;
        } catch (IOException e) {
            throw new IOException("Failed to create connection to: " + originalHost, e);
        }
    }

    /**
     * T643 / §XXXVII.5 — delegates to the single hardened validator
     * {@link com.viglet.turing.spring.security.ssrf.TurSsrfGuard} so this
     * long-standing download-path guard also blocks IPv6 ULA ({@code fc00::/7}),
     * closing the gap where {@code isSiteLocalAddress()} only matched the
     * deprecated {@code fec0::/10}.
     */
    public static boolean isSafe(InetAddress address) {
        return com.viglet.turing.spring.security.ssrf.TurSsrfGuard.isSafeAddress(address);
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
        return VigletContentExtractor.parseDocument(stream);
    }

    /**
     * T739 / §XLVIII — parse an already-fetched HTML document (e.g. the rendered
     * DOM returned by a headless-browser sidecar) into plain text via the same
     * Tika extraction the URL/file paths use. No network access — the HTML is
     * supplied by the caller, which is responsible for having SSRF-guarded and
     * fetched it. Returns an empty {@link TurFileAttributes} (never {@code null})
     * when the input is blank or unparseable.
     *
     * @param html the raw HTML markup
     * @param name a display name for the source (used as the file name / title)
     */
    public static TurFileAttributes htmlToText(String html, String name) {
        if (html == null || html.isBlank()) {
            return new TurFileAttributes();
        }
        try (InputStream in = new java.io.ByteArrayInputStream(
                html.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            String text = parseDocument(in).orElse("");
            return TurFileAttributes.builder()
                    .name(name)
                    .title(name)
                    .content(text)
                    .build();
        } catch (IOException e) {
            log.error("Error parsing rendered HTML for {}: {}", name, e.getMessage());
            return new TurFileAttributes();
        }
    }

    private static File createTempFile() throws IOException {
        return File.createTempFile(UUID.randomUUID().toString(), null,
                TurCommonsUtils.addSubDirToStoreDir(TMP));
    }
}
