/*
 * Copyright (C) 2016-2022 the original author or authors.
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

package com.viglet.turing.commons.utils;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.KeyValue;
import org.apache.commons.collections4.keyvalue.DefaultMapEntry;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.jsoup.Jsoup;

import com.viglet.core.commons.io.VigletStoreDirectory;
import com.viglet.core.commons.io.VigletZipUtils;
import com.viglet.core.commons.json.VigletJson;
import com.viglet.turing.commons.exception.TurException;

import lombok.extern.slf4j.Slf4j;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Slf4j
public class TurCommonsUtils {
    public static final String COLON = ":";

    private TurCommonsUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static Optional<KeyValue<String, String>> getKeyValueFromColon(String stringWithColon) {
        String[] attributeKV = stringWithColon.split(COLON);
        if (attributeKV.length >= 2) {
            String key = attributeKV[0];
            String value = Arrays.stream(attributeKV).skip(1).collect(Collectors.joining(COLON));
            return Optional.of(new DefaultMapEntry<>(key, value));
        } else {
            return Optional.empty();
        }
    }

    public static boolean isValidUrl(URL url) {

        UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);
        if (urlValidator.isValid(url.toString())) {
            return true;
        } else {
            log.error("Invalid URL: {}", url);
            return false;
        }
    }

    public static String html2Text(String text) {
        return Jsoup.parse(text).text();
    }

    public static String text2Description(String text, int maxLength) {
        if (text != null && text.length() > maxLength) {
            BreakIterator bi = BreakIterator.getWordInstance();
            bi.setText(text);

            if (bi.isBoundary(maxLength - 1)) {
                return text.substring(0, maxLength - 2) + " ...";
            } else {
                int preceding = bi.preceding(maxLength - 1);
                return text.substring(0, preceding - 1) + " ...";
            }
        } else {
            return text + " ...";
        }
    }

    public static String html2Description(String text, int numberChars) {
        return text2Description(html2Text(text), numberChars);
    }

    public static URI addOrReplaceParameter(URI uri, String paramName, Locale locale,
            boolean decoded) {
        return addOrReplaceParameter(uri, paramName, locale.toLanguageTag(), decoded);
    }

    public static URI addOrReplaceParameter(URI uri, String paramName, String paramValue,
            boolean decoded) {
        List<NameValuePair> params = new URIBuilder(uri, StandardCharsets.ISO_8859_1).getQueryParams();
        StringBuilder sbQueryString = new StringBuilder();
        boolean alreadyExists = false;
        for (NameValuePair nameValuePair : params) {
            if (nameValuePair == null || nameValuePair.getName() == null) {
                continue;
            }
            String value = nameValuePair.getValue();
            String name = nameValuePair.getName();
            if (name.equals(paramName) && !alreadyExists) {
                alreadyExists = true;
                addParameterToQueryString(sbQueryString, name, paramValue);
            } else {
                String paramVal = value;
                if (decoded && value != null) {
                    paramVal = URLDecoder.decode(value, StandardCharsets.UTF_8);
                }
                addParameterToQueryString(sbQueryString, name, paramVal);
            }
        }
        if (!alreadyExists) {
            addParameterToQueryString(sbQueryString, paramName, paramValue);
        }

        return modifiedURI(uri, sbQueryString);
    }

    public static void addParameterToQueryString(StringBuilder sbQueryString, String name,
            String value) {
        if (value != null) {
            // Percent-encode the value here (not via the URI constructor in
            // modifiedURI) so structural delimiters — above all '&' — inside a
            // value cannot be read as parameter separators. The parameter name is
            // an internal constant (q, p, sort, fq[], …) and is emitted literally.
            sbQueryString.append(name).append('=').append(encodeQueryValue(value)).append('&');
        }
    }

    private static final String QUERY_VALUE_SAFE =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~:/@,;!$'()*?";
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    /**
     * Percent-encodes a query-parameter <em>value</em> per RFC 3986. Unreserved
     * characters and the sub-delimiters that are safe to keep literal in a value
     * ({@code : / @ , ; ! $ ' ( ) * ?}) are preserved so links stay readable and
     * backward-compatible; everything else — crucially the structural delimiters
     * {@code & = + # %}, whitespace and non-ASCII — is percent-encoded as UTF-8.
     *
     * <p>This is required because a raw {@code &} inside a value (e.g. a facet
     * value {@code "RAG & Chat"}) would otherwise be read as a parameter
     * separator, truncating the filter and returning no results. The
     * multi-argument {@link URI} constructor cannot do this job: it leaves
     * {@code &}/{@code =} literal (they are legal query characters) and
     * double-encodes existing {@code %XX} escapes ({@code %26} &rarr;
     * {@code %2526}) — so {@link #modifiedURI(URI, StringBuilder)} sets the
     * already-encoded query verbatim instead.
     */
    private static String encodeQueryValue(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (c < 0x80 && QUERY_VALUE_SAFE.indexOf(c) >= 0) {
                out.append((char) c);
            } else {
                out.append('%').append(HEX[c >> 4]).append(HEX[c & 0x0F]);
            }
        }
        return out.toString();
    }

    public static URI modifiedURI(URI uri, StringBuilder sbQueryString) {
        String query = removeAmpersand(sbQueryString);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        try {
            // addParameterToQueryString has already percent-encoded every value,
            // so set the query verbatim. Routing it through the multi-argument URI
            // constructor would be wrong twice over: it leaves reserved sub-delims
            // ('&', '=') literal — so an '&' inside a value would split the params —
            // and it double-encodes existing "%XX" escapes ("%26" -> "%2526"). The
            // single-string URI parser keeps the encoded query and the literal
            // "fq[]" brackets intact.
            return new URI(query.isEmpty() ? path : path + "?" + query);
        } catch (URISyntaxException e) {
            log.error("Failed to build URI from path '{}' with query '{}': {}",
                    path, query, e.getMessage(), e);
        }
        return uri;
    }

    private static String removeAmpersand(StringBuilder sbQueryString) {
        if (!sbQueryString.isEmpty()) {
            return sbQueryString.substring(0, sbQueryString.toString().length() - 1);
        }
        return "";
    }

    public static String cleanTextContent(String text) {
        text = text.replaceAll("[\r\n\t]", " ");
        // Remove 2 or more spaces
        text = text.trim().replaceAll(" +", " ");
        return text.trim();
    }

    public static List<String> cloneListOfTermsAsString(List<?> attributeArray) {
        return attributeArray.stream()
                .map(Object::toString)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Add all files from the source directory to the destination zip file.
     *
     * <p>Delegates to {@link VigletZipUtils#addFilesToZip(File, File)}
     * (Block Q / T375).
     *
     * @param source      the directory with files to add
     * @param destination the zip file that should contain the files
     */
    public static void addFilesToZip(File source, File destination) {
        VigletZipUtils.addFilesToZip(source, destination);
    }

    public static File getStoreDir() {
        return VigletStoreDirectory.getStoreDir();
    }

    public static File addSubDirToStoreDir(String directoryName) {
        return VigletStoreDirectory.addSubDirToStoreDir(directoryName);
    }

    /**
     * Unzip it.
     *
     * <p>Delegates to {@link VigletZipUtils#unZipIt(File, File)}
     * (Block Q / T375).
     *
     * @param file         input zip file
     * @param outputFolder output Folder
     */
    public static void unZipIt(File file, File outputFolder) {
        VigletZipUtils.unZipIt(file, outputFolder);
    }

    public static boolean isValidJson(String test) {
        return VigletJson.isValidJson(test);
    }

    public static String asJsonString(final Object obj) throws TurException {
        try {
            return VigletJson.asJsonString(obj);
        } catch (Exception e) {
            throw new TurException(e);
        }
    }

    public static File getTempDirectory() {
        return VigletStoreDirectory.getTempDirectory();
    }
}
