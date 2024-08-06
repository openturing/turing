/*
 * Copyright (C) 2016-2022 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.turing.commons.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.viglet.turing.commons.exception.TurException;
import lombok.extern.slf4j.Slf4j;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URLEncodedUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.BreakIterator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @author Alexandre Oliveira
 * @since 0.3.6
 */
@Slf4j
public class TurCommonsUtils {
    private static final String USER_DIR = "user.dir";
    private static final File userDir = new File(System.getProperty(USER_DIR));

    private TurCommonsUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static String html2Text(String text) {
        return Jsoup.parse(text).text();
    }

    public static String text2Description(String text, int maxLength) {
        if(text != null && text.length() > maxLength) {
            BreakIterator bi = BreakIterator.getWordInstance();
            bi.setText(text);

            if(bi.isBoundary(maxLength-1)) {
                return text.substring(0, maxLength-2) + " ...";
            } else {
                int preceding = bi.preceding(maxLength-1);
                return text.substring(0, preceding-1) + " ...";
            }
        } else {
            return text + " ...";
        }
    }

    public static String html2Description(String text, int number_chars) {
        return text2Description(html2Text(text), number_chars);
    }

    public static URI addOrReplaceParameter(URI uri, String paramName, Locale locale) {
       return addOrReplaceParameter(uri, paramName, locale.toLanguageTag());
    }
    public static URI addOrReplaceParameter(URI uri, String paramName, String paramValue) {

        List<NameValuePair> params =   URLEncodedUtils.parse(uri, StandardCharsets.UTF_8);

        StringBuilder sbQueryString = new StringBuilder();
        boolean alreadyExists = false;

        for (NameValuePair nameValuePair : params) {
            if ((nameValuePair.getName().equals(paramName) && !alreadyExists)) {
                alreadyExists = true;
                addParameterToQueryString(sbQueryString, nameValuePair.getName(), paramValue);
            } else {
                addParameterToQueryString(sbQueryString, nameValuePair.getName(), nameValuePair.getValue());
            }
        }
        if (!alreadyExists) {
            addParameterToQueryString(sbQueryString, paramName, paramValue);
        }

        return modifiedURI(uri, sbQueryString);
    }

    public static void addParameterToQueryString(StringBuilder sbQueryString, String name, String value) {
        if (value != null) {
            sbQueryString.append(String.format("%s=%s&", name, URLEncoder.encode(value, StandardCharsets.UTF_8)));
        }
    }

    public static URI modifiedURI(URI uri, StringBuilder sbQueryString) {
        try {
            return new URI(uri.getRawPath() + "?" + removeAmpersand(sbQueryString));
        } catch (URISyntaxException e) {
            log.error(e.getMessage(), e);
        }
        return uri;
    }

    private static String removeAmpersand(StringBuilder sbQueryString) {
        if (!sbQueryString.toString().isEmpty()) {
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

    public static List<String> cloneListOfTermsAsString(List<?> nlpAttributeArray) {
        List<String> list = new ArrayList<>();
        for (Object nlpAttributeItem : nlpAttributeArray) {
            list.add((String) nlpAttributeItem);
        }
        return list;
    }

    public static String stripAccents(String s) {
        s = Normalizer.normalize(s, Normalizer.Form.NFD);
        s = s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        return s;
    }

    public static String removeDuplicateWhiteSpaces(String s) {
        return s.replaceAll("\\s+", " ").trim();
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

            FileUtils.listFiles(source, null, true).forEach(file -> addFileToZip(source, archive, file));

            archive.finish();
        } catch (IOException | ArchiveException e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void addFileToZip(File source, ArchiveOutputStream<ZipArchiveEntry> archive, File file) {
        String entryName;
        try {
            entryName = getEntryName(source, file);
            ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
            archive.putArchiveEntry(entry);

            try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(file.toPath()))) {
                IOUtils.copy(input, archive);

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
     * @throws IOException if the io fails
     */
    private static String getEntryName(File source, File file) throws IOException {
        int index = source.getAbsolutePath().length() + 1;
        String path = file.getCanonicalPath();

        return path.substring(index);
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

    public static boolean isJSONValid(String test) {
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
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new TurException(e);
        }
    }

    public static File getTempDirectory() {
        return TurCommonsUtils.addSubDirToStoreDir("tmp");
    }
}
