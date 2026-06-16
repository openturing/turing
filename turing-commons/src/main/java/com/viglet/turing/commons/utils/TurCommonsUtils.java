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
            sbQueryString.append(String.format("%s=%s&", name, value));
        }
    }

    public static URI modifiedURI(URI uri, StringBuilder sbQueryString) {
        String query = removeAmpersand(sbQueryString);
        try {
            // Use the multi-argument URI constructor so any character that needs
            // quoting (non-ASCII like 'é', whitespace, '"', etc.) is percent-encoded.
            // All callers pass decoded values from URIBuilder.getQueryParams(), so we
            // don't risk double-encoding pre-existing pct-encoded sequences.
            return new URI(null, null, uri.getRawPath(), query.isEmpty() ? null : query, null);
        } catch (URISyntaxException e) {
            log.error("Failed to build URI from path '{}' with query '{}': {}",
                    uri.getRawPath(), query, e.getMessage(), e);
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
