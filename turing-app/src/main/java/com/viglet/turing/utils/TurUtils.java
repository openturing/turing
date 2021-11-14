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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.UUID;

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
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.exception.TurException;
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

	public static File extractZipFile(MultipartFile file) {
		File userDir = new File(System.getProperty("user.dir"));
		if (userDir.exists() && userDir.isDirectory()) {
			File tmpDir = new File(userDir.getAbsolutePath().concat(File.separator + "store" + File.separator + "tmp"));
			if (!tmpDir.exists()) {
				tmpDir.mkdirs();
			}

			File zipFile = new File(tmpDir.getAbsolutePath().concat(File.separator + "imp_"
					+ file.getOriginalFilename().replace(".", "").replace("/", "") + UUID.randomUUID())); // NOSONAR
			try {
				file.transferTo(zipFile);
				File extractFolder = new File(
						tmpDir.getAbsolutePath().concat(File.separator + "imp_" + UUID.randomUUID()));

				unZipIt(zipFile, extractFolder);

				FileUtils.deleteQuietly(zipFile);
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
			}
		}
		return null;
	}

	/**
	 * Unzip it
	 * 
	 * @param zipFile      input zip file
	 * @param outputFolder output Folder
	 * @throws Exception
	 */
	public static void unZipIt(File zipFile, File outputFolder) {

		try (ZipArchiveInputStream zin = new ZipArchiveInputStream(new FileInputStream(zipFile))) {
			ZipArchiveEntry entry;
			while ((entry = zin.getNextZipEntry()) != null) {
				if (entry.isDirectory()) {
					continue;
				}
				File curfile = new File(outputFolder, entry.getName());
				if (!curfile.toPath().normalize().startsWith(outputFolder.toPath()))
					throw new TurException("Bad zip entry");
				File parent = curfile.getParentFile();
				if (!parent.exists() && !parent.mkdirs()) {
					throw new TurException("could not create directory: " + parent.getPath());
				}
				IOUtils.copy(zin, new FileOutputStream(curfile));
			}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
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
