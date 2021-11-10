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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.viglet.turing.spring.security.auth.ITurAuthenticationFacade;

@Component
public class TurUtils {
	private static final Log logger = LogFactory.getLog(TurUtils.class);
	@Autowired
	private ITurAuthenticationFacade authenticationFacade;

	public String getCurrentUsername() {
		Authentication authentication = authenticationFacade.getAuthentication();
		return authentication.getName();
	}

	public String stripAccents(String s) {
		s = Normalizer.normalize(s, Normalizer.Form.NFD);
		s = s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
		return s;
	}

	public String removeDuplicateWhiteSpaces(String s) {
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
		OutputStream archiveStream;
		try {
			archiveStream = new FileOutputStream(destination);

			try (ArchiveOutputStream archive = new ArchiveStreamFactory()
					.createArchiveOutputStream(ArchiveStreamFactory.ZIP, archiveStream)) {

				Collection<File> fileList = FileUtils.listFiles(source, null, true);

				for (File file : fileList) {
					String entryName = getEntryName(source, file);
					ZipArchiveEntry entry = new ZipArchiveEntry(entryName);
					archive.putArchiveEntry(entry);

					BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));

					IOUtils.copy(input, archive);
					input.close();
					archive.closeArchiveEntry();
				}

				archive.finish();
			} catch (IOException | ArchiveException e) {
				logger.error(e.getMessage(), e);
			}
		} catch (FileNotFoundException e) {
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

	/**
	 * Unzip it
	 * 
	 * @param zipFile      input zip file
	 * @param outputFolder output Folder
	 * @throws IOException if the IO fails
	 */
	public void unZipIt(File zipFile, File outputFolder) throws IOException {

		try (ZipArchiveInputStream zin = new ZipArchiveInputStream(new FileInputStream(zipFile))) {
			ZipArchiveEntry entry;
			while ((entry = zin.getNextZipEntry()) != null) {
				if (entry.isDirectory()) {
					continue;
				}
				File curfile = new File(outputFolder, entry.getName());
				File parent = curfile.getParentFile();
				if (!parent.exists()) {
					if (!parent.mkdirs()) {
						throw new RuntimeException("could not create directory: " + parent.getPath());
					}
				}
				IOUtils.copy(zin, new FileOutputStream(curfile));
			}
		}
	}

	public boolean isJSONValid(String test) {
		try {
			new JSONObject(test);
		} catch (JSONException ex) {
			// edited, to include @Arthur's comment
			// e.g. in case JSONArray is valid as well...
			try {
				new JSONArray(test);
			} catch (JSONException ex1) {
				return false;
			}
		}
		return true;
	}

	public String removeUrl(String commentstr) {
		String urlPattern = "((https?|ftp|gopher|telnet|file|Unsure):([(//)(\\\\\\\\)])+[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]*)";
		Pattern p = Pattern.compile(urlPattern, Pattern.CASE_INSENSITIVE);
		Matcher m = p.matcher(commentstr);
		int i = 0;
		while (m.find()) {
			commentstr = commentstr.replaceAll(m.group(i), "").trim();
			i++;
		}
		return commentstr;
	}
}
