/*
 * Copyright (C) 2016-2019 the original author or authors. 
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

package com.viglet.turing.exchange;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.exchange.sn.TurSNSiteExchange;
import com.viglet.turing.exchange.sn.TurSNSiteImport;
import com.viglet.turing.spring.utils.TurSpringUtils;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class TurImportExchange {

	private final TurSNSiteImport turSNSiteImport;
	private static final String EXPORT_FILE = "export.json";

	public TurImportExchange(TurSNSiteImport turSNSiteImport) {
		this.turSNSiteImport = turSNSiteImport;
	}

	public TurExchange importFromMultipartFile(MultipartFile multipartFile) {
		File extractFolder = this.extractZipFile(multipartFile);
		File parentExtractFolder = null;

		if (extractFolder != null) {
			// Check if export.json exists, if it is not exist try access a sub directory
			if (!(new File(extractFolder, EXPORT_FILE).exists())
					&& (Objects.requireNonNull(extractFolder.listFiles()).length == 1)) {
				for (File fileOrDirectory : Objects.requireNonNull(extractFolder.listFiles())) {
					if (fileOrDirectory.isDirectory() && new File(fileOrDirectory, EXPORT_FILE).exists()) {
						parentExtractFolder = extractFolder;
						extractFolder = fileOrDirectory;
					}
				}
			}
			importSNSiteFromExportFile(extractFolder, parentExtractFolder);
		}
		return new TurExchange();
	}

	private void importSNSiteFromExportFile(File extractFolder, File parentExtractFolder) {
		ObjectMapper mapper = JsonMapper.builder().build();

		try (FileInputStream fis = new FileInputStream(
				extractFolder.getAbsolutePath().concat(File.separator).concat(EXPORT_FILE))) {

			TurExchange turExchange = mapper.readValue(fis, TurExchange.class);
			processTurExchange(turExchange);
			cleanupDirectories(extractFolder, parentExtractFolder);
		} catch (Exception e) {
			log.error("Error importing SN Site: {}", e.getMessage(), e);
		}
	}

	private void processTurExchange(TurExchange turExchange) {
		if (turExchange.getSnSites() != null && !turExchange.getSnSites().isEmpty()) {
			logSNSites(turExchange.getSnSites());
			turSNSiteImport.importSNSite(turExchange);
		} else {
			log.warn("No SN Sites found in export file");
		}
	}

	private void logSNSites(java.util.List<TurSNSiteExchange> sites) {
		for (TurSNSiteExchange site : sites) {
			log.info(
					"Deserialized SN Site '{}': fields={}, fieldExts={}, locales={}, spotlights={}, rankings={}",
					site.getName(),
					formatSize(site.getTurSNSiteFields()),
					formatSize(site.getTurSNSiteFieldExts()),
					formatSize(site.getTurSNSiteLocales()),
					formatSize(site.getTurSNSiteSpotlights()),
					formatSize(site.getTurSNRankingExpressions()));
		}
	}

	private String formatSize(java.util.Collection<?> collection) {
		return collection != null ? String.valueOf(collection.size()) : "null";
	}

	private void cleanupDirectories(File extractFolder, File parentExtractFolder) throws IOException {
		FileUtils.deleteDirectory(extractFolder);
		if (parentExtractFolder != null) {
			FileUtils.deleteDirectory(parentExtractFolder);
		}
	}

	public TurExchange parseExportFile(MultipartFile multipartFile) {
		File extractFolder = this.extractZipFile(multipartFile);
		if (extractFolder == null) {
			return new TurExchange();
		}
		File parentExtractFolder = null;
		if (!(new File(extractFolder, EXPORT_FILE).exists())
				&& (Objects.requireNonNull(extractFolder.listFiles()).length == 1)) {
			for (File fileOrDirectory : Objects.requireNonNull(extractFolder.listFiles())) {
				if (fileOrDirectory.isDirectory() && new File(fileOrDirectory, EXPORT_FILE).exists()) {
					parentExtractFolder = extractFolder;
					extractFolder = fileOrDirectory;
				}
			}
		}
		ObjectMapper mapper = JsonMapper.builder().build();
		try (FileInputStream fis = new FileInputStream(
				extractFolder.getAbsolutePath().concat(File.separator).concat(EXPORT_FILE))) {
			return mapper.readValue(fis, TurExchange.class);
		} catch (Exception e) {
			log.error("Error parsing export file: {}", e.getMessage(), e);
			return new TurExchange();
		} finally {
			try {
				cleanupDirectories(extractFolder, parentExtractFolder);
			} catch (IOException e) {
				log.warn("Cleanup failed: {}", e.getMessage(), e);
			}
		}
	}

	public TurExchange importFromFile(File file) {

		try (FileInputStream input = new FileInputStream(file)) {
			MultipartFile multipartFile = new MockMultipartFile(file.getName(), IOUtils.toByteArray(input));
			return this.importFromMultipartFile(multipartFile);
		} catch (IOException | IllegalStateException e) {
			log.error(e.getMessage(), e);
		}
		return new TurExchange();
	}

	public File extractZipFile(MultipartFile file) {
		return TurSpringUtils.extractZipFile(file);
	}
}
