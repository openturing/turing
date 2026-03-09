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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.collections4.KeyValue;
import org.apache.commons.collections4.keyvalue.DefaultMapEntry;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.io.FileUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import com.viglet.turing.commons.exception.TurException;

import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Slf4j
public class TurCommonsUtils {
    private static final String USER_DIR = "user.dir";
    private static final File userDir = new File(System.getProperty(USER_DIR));
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
        try {
            return new URI(uri.getRawPath() + "?" + removeAmpersand(sbQueryString)
                    .replaceAll("[\\t\\n\\r]+", "%20").replace(" ", "%20").replace("\"", "%22"));
        } catch (URISyntaxException e) {
            log.error(e.getMessage(), e);
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
                .map(String.class::cast)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Add all files from the source directory to the destination zip file
     *
     * @param source      the directory with files to add
     * @param destination the zip file that should contain the files
     */
    public static void addFilesToZip(File source, File destination) {

        try (OutputStream archiveStream = Files.newOutputStream(destination.toPath());
                ArchiveOutputStream<ZipArchiveEntry> archive = new ArchiveStreamFactory()
                        .createArchiveOutputStream(ArchiveStreamFactory.ZIP, archiveStream)) {

            FileUtils.listFiles(source, null, true)
                    .forEach(file -> addFileToZip(source, archive, file));

            archive.finish();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void addFileToZip(File source, ArchiveOutputStream<ZipArchiveEntry> archive,
            File file) {
        String entryName;
        try {
            entryName = getEntryName(source, file);
            ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
            archive.putArchiveEntry(entry);

            try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
                input.transferTo(archive);
                archive.closeArchiveEntry();
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * Remove the leading part of each entry that contains the source directory name
     *
     * @param source the directory where the file entry is found
     * @param file   the file that is about to be added
     * @return the name of an archive entry
     */
    private static String getEntryName(File source, File file) {
        Path sourcePath = source.toPath().toAbsolutePath().normalize();
        Path filePath = file.toPath().toAbsolutePath().normalize();
        return sourcePath.relativize(filePath).toString();
    }

    public static File getStoreDir() {
        File store = new File(userDir.getAbsolutePath().concat(File.separator + "store"));
        try {
            Files.createDirectories(store.toPath());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return store;
    }

    public static File addSubDirToStoreDir(String directoryName) {
        File storeDir = getStoreDir();
        File newDir = new File(storeDir.getAbsolutePath().concat(File.separator + directoryName));
        try {
            Files.createDirectories(newDir.toPath());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return newDir;
    }

    /**
     * Unzip it
     *
     * @param file         input zip file
     * @param outputFolder output Folder
     */
    public static void unZipIt(File file, File outputFolder) {
        try (ZipFile zipFile = new ZipFile(file)) {
            zipFile.extractAll(outputFolder.getAbsolutePath());
        } catch (IllegalStateException | IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public static boolean isValidJson(String test) {
        try {
            new JSONObject(test);
        } catch (JSONException ex) {
            try {
                new JSONArray(test);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }

    public static String asJsonString(final Object obj) throws TurException {
        try {
            // No Jackson 3, usamos o Builder para configurar e criar o mapper
            JsonMapper mapper = JsonMapper.builder()
                    .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
                    .build();

            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new TurException(e);
        }
    }

    public static File getTempDirectory() {
        return addSubDirToStoreDir("tmp");
    }
}
