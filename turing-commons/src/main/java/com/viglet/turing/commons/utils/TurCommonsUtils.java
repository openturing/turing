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

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.validator.routines.UrlValidator;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import net.lingala.zip4j.ZipFile;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.6
 *
 */
public class TurCommonsUtils {
	private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

	private static final String USER_DIR = "user.dir";
	private static final File userDir = new File(System.getProperty(USER_DIR));

	private TurCommonsUtils() {
		throw new IllegalStateException("Utility class");
	}

	public static boolean isValidUrl(URL url) {

		UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);
		if (urlValidator.isValid(url.toString())) {
			return true;
		} else {
			logger.error("Invalid URL: {}", url);
			return false;
		}
	}
	public static URI addOrReplaceParameter(URI uri, String paramName, String paramValue) {

		List<NameValuePair> params = URLEncodedUtils.parse(uri, StandardCharsets.UTF_8.name());

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
        try {
            sbQueryString.append(String.format("%s=%s&", name, URLEncoder.encode(value, "UTF-8")));
        } catch (UnsupportedEncodingException e) {
            logger.error(e.getMessage(), e);
        }
    }

	public static URI modifiedURI(URI uri, StringBuilder sbQueryString) {
		try {
			return new URI(uri.getRawPath() + "?" + removeAmpersand(sbQueryString));
		} catch (URISyntaxException e) {
			logger.error(e.getMessage(), e);
		}
		return uri;
	}

	private static String removeAmpersand(StringBuilder sbQueryString) {
		return sbQueryString.toString().substring(0, sbQueryString.toString().length() - 1);
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
	 * @throws IOException      if the io fails
	 * @throws ArchiveException if creating or adding to the archive fails
	 */
	public static void addFilesToZip(File source, File destination) {

		try (OutputStream archiveStream = new FileOutputStream(destination);
				ArchiveOutputStream archive = new ArchiveStreamFactory()
						.createArchiveOutputStream(ArchiveStreamFactory.ZIP, archiveStream)) {

			FileUtils.listFiles(source, null, true).forEach(file -> addFileToZip(source, archive, file));

			archive.finish();
		} catch (IOException | ArchiveException e) {
			logger.error(e.getMessage(), e);
		}
	}

	private static void addFileToZip(File source, ArchiveOutputStream archive, File file) {
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
	private static String getEntryName(File source, File file) throws IOException {
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

	public static File getTempDirectory() {
		return addSubDirToStoreDir("tmp");
	}
}
