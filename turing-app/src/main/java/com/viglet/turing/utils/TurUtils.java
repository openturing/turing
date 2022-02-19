/*
 * Copyright (C) 2016-2021 the original author or authors.
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

package com.viglet.turing.utils;

import com.viglet.turing.spring.security.auth.ITurAuthenticationFacade;
import net.lingala.zip4j.ZipFile;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class TurUtils {
    private static final Logger logger = LogManager.getLogger(TurUtils.class);
    @Autowired
    private ITurAuthenticationFacade authenticationFacade;
    private static final String USER_DIR = "user.dir";
    private static final File userDir = new File(System.getProperty(USER_DIR));

    public static String cleanTextContent(String text) {
        if (logger.isDebugEnabled()) {
            logger.debug("Original Text: {}", text.replace("\n", "\\\\n \n").replace("\t", "\\\\t \t"));
        }
        // Remove 2 or more spaces
        text = text.trim().replaceAll("[\\t\\r]", "\\n");
        text = text.trim().replaceAll(" +", " ");

        text = text.trim();

        if (logger.isDebugEnabled()) {
            logger.debug("Cleaned Text: {}", text);
        }
        return text;
    }

    public List<String> cloneListOfTermsAsString(List<?> nlpAttributeArray) {
        List<String> list = new ArrayList<>();
        for (Object nlpAttributeItem : nlpAttributeArray) {
            list.add((String) nlpAttributeItem);
        }
        return list;
    }

    public String getCurrentUsername() {
        Authentication authentication = authenticationFacade.getAuthentication();
        return authentication.getName();
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
     * @throws IOException      if the io fails
     * @throws ArchiveException if creating or adding to the archive fails
     */
    public void addFilesToZip(File source, File destination) {

        try (OutputStream archiveStream = new FileOutputStream(destination);
             ArchiveOutputStream archive = new ArchiveStreamFactory()
                     .createArchiveOutputStream(ArchiveStreamFactory.ZIP, archiveStream)) {

            FileUtils.listFiles(source, null, true).forEach(file -> addFileToZip(source, archive, file));

            archive.finish();
        } catch (IOException | ArchiveException e) {
            logger.error(e.getMessage(), e);
        }
    }

    private void addFileToZip(File source, ArchiveOutputStream archive, File file) {
        String entryName;
        try {
            entryName = getEntryName(source, file);
            ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
            archive.putArchiveEntry(entry);

            try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file))) {
                IOUtils.copy(input, archive);
                archive.closeArchiveEntry();
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
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
    private String getEntryName(File source, File file) throws IOException {
        int index = source.getAbsolutePath().length() + 1;
        String path = file.getCanonicalPath();

        return path.substring(index);
    }

    public static File getStoreDir() {
        File store = new File(userDir.getAbsolutePath().concat(File.separator + "store"));
        if (!store.exists()) {
            store.mkdirs();
        }
        return store;
    }

    public static File addSubDirToStoreDir(String directoryName) {
        File storeDir = getStoreDir();
        File newDir = new File(storeDir.getAbsolutePath().concat(File.separator + directoryName));
        if (!newDir.exists()) {
            newDir.mkdirs();
        }
        return newDir;
    }

    public static File extractZipFile(MultipartFile file) {
        File tmpDir = addSubDirToStoreDir("tmp");

        File zipFile = new File(tmpDir.getAbsolutePath().concat(File.separator + "imp_"
                + file.getOriginalFilename().replace(".", "").replace("/", "") + UUID.randomUUID())); // NOSONAR

        try {
            file.transferTo(zipFile);
        } catch (IllegalStateException | IOException e) {
            logger.error(e.getMessage(), e);
        }
        File extractFolder = new File(tmpDir.getAbsolutePath().concat(File.separator + "imp_" + UUID.randomUUID()));
        try {
            unZipIt(zipFile, extractFolder);
            FileUtils.deleteQuietly(zipFile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

        return extractFolder;
    }

    /**
     * Unzip it
     *
     * @param file         input zip file
     * @param outputFolder output Folder
     * @throws Exception
     */
    public static void unZipIt(File file, File outputFolder) {
        try (ZipFile zipFile = new ZipFile(file)) {
            zipFile.extractAll(outputFolder.getAbsolutePath());
        } catch (IllegalStateException | IOException e) {
            logger.error(e);
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
}
