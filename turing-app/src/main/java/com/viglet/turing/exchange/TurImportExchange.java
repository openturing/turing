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
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.exchange.sn.TurSNSiteContentExchangeService;
import com.viglet.turing.exchange.sn.TurSNSiteExchange;
import com.viglet.turing.exchange.sn.TurSNSiteImport;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.spring.utils.TurSpringUtils;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Component
public class TurImportExchange {

	private final TurSNSiteImport turSNSiteImport;
	private final TurSNSiteContentExchangeService contentExchangeService;
	private final TurSNSiteRepository turSNSiteRepository;
	private final TurStorageService storageService;
	private static final String EXPORT_FILE = "export.json";
	private static final String CONTENT_SUFFIX = "_content.json";
	private static final String APP_DIR = "app";
	private static final String TURING_MANIFEST = "turing-manifest.json";
	private static final String PAGES_PREFIX = "public/";

	public TurImportExchange(TurSNSiteImport turSNSiteImport,
			TurSNSiteContentExchangeService contentExchangeService,
			TurSNSiteRepository turSNSiteRepository,
			TurStorageService storageService) {
		this.turSNSiteImport = turSNSiteImport;
		this.contentExchangeService = contentExchangeService;
		this.turSNSiteRepository = turSNSiteRepository;
		this.storageService = storageService;
	}

	public TurExchange importFromMultipartFile(MultipartFile multipartFile) {
		return importFromMultipartFile(multipartFile, false, null);
	}

	public TurExchange importFromMultipartFile(MultipartFile multipartFile,
			boolean includeContent, boolean includeTemplate, String taskId) {
		File extractFolder = this.extractZipFile(multipartFile);
		File parentExtractFolder = null;

		if (extractFolder != null) {
			if (!(new File(extractFolder, EXPORT_FILE).exists())
					&& (Objects.requireNonNull(extractFolder.listFiles()).length == 1)) {
				for (File fileOrDirectory : Objects.requireNonNull(extractFolder.listFiles())) {
					if (fileOrDirectory.isDirectory() && new File(fileOrDirectory, EXPORT_FILE).exists()) {
						parentExtractFolder = extractFolder;
						extractFolder = fileOrDirectory;
					}
				}
			}
			boolean needsCleanup = !includeContent && !includeTemplate;
			importSNSiteFromExportFile(extractFolder, parentExtractFolder, needsCleanup);

			if (includeContent) {
				importContentFiles(extractFolder, taskId);
			}

			if (includeTemplate) {
				importTemplateFromAppFolder(extractFolder);
			}

			if (!needsCleanup) {
				try {
					cleanupDirectories(extractFolder, parentExtractFolder);
				} catch (IOException e) {
					log.warn("Cleanup failed: {}", e.getMessage(), e);
				}
			}
		}
		return new TurExchange();
	}

	public TurExchange importFromMultipartFile(MultipartFile multipartFile,
			boolean includeContent, String taskId) {
		return importFromMultipartFile(multipartFile, includeContent, false, taskId);
	}

	/**
	 * Checks the ZIP for available import options: content files and valid SPA template.
	 */
	public Map<String, Object> checkZip(MultipartFile multipartFile) {
		File extractFolder = this.extractZipFile(multipartFile);
		if (extractFolder == null) {
			return Map.of("hasContent", false, "hasTemplate", false);
		}
		File workFolder = resolveExportFolder(extractFolder);
		boolean hasContent = findContentFiles(workFolder).length > 0;
		boolean hasTemplate = hasValidTemplate(workFolder);
		String templateName = null;
		if (hasTemplate) {
			templateName = readTemplateName(workFolder);
		}
		try {
			cleanupDirectories(extractFolder, null);
		} catch (IOException e) {
			log.warn("Cleanup failed: {}", e.getMessage(), e);
		}
		Map<String, Object> result = new java.util.HashMap<>();
		result.put("hasContent", hasContent);
		result.put("hasTemplate", hasTemplate);
		result.put("templateName", templateName);
		return result;
	}

	private boolean hasValidTemplate(File workFolder) {
		File appDir = new File(workFolder, APP_DIR);
		if (!appDir.isDirectory()) return false;
		File manifest = new File(appDir, TURING_MANIFEST);
		if (!manifest.isFile()) return false;
		try {
			ObjectMapper mapper = JsonMapper.builder().build();
			Map<String, Object> json = mapper.readValue(manifest, new TypeReference<>() {});
			return json.containsKey("name") && json.get("name") != null
					&& !json.get("name").toString().isBlank();
		} catch (Exception e) {
			log.debug("Invalid turing-manifest.json in app/: {}", e.getMessage());
			return false;
		}
	}

	private String readTemplateName(File workFolder) {
		File manifest = new File(new File(workFolder, APP_DIR), TURING_MANIFEST);
		try {
			ObjectMapper mapper = JsonMapper.builder().build();
			Map<String, Object> json = mapper.readValue(manifest, new TypeReference<>() {});
			Object name = json.get("name");
			return name != null ? name.toString() : null;
		} catch (Exception e) {
			return null;
		}
	}

	private void importTemplateFromAppFolder(File extractFolder) {
		File appDir = new File(extractFolder, APP_DIR);
		if (!appDir.isDirectory() || !storageService.isEnabled()) {
			log.debug("Skipping template import: app/ dir missing or storage disabled");
			return;
		}
		File manifest = new File(appDir, TURING_MANIFEST);
		if (!manifest.isFile()) {
			log.debug("Skipping template import: no valid turing-manifest.json");
			return;
		}
		String templateName = readTemplateName(extractFolder);
		if (templateName == null || templateName.isBlank()) {
			log.warn("Skipping template import: turing-manifest.json has no name");
			return;
		}
		String prefix = PAGES_PREFIX + templateName + "/";
		// Clear existing template files
		storageService.deleteObjectsWithPrefix(prefix);
		// Upload all files from app/ to storage
		int count = uploadDirectory(appDir, prefix, appDir);
		log.info("Imported SPA template '{}' ({} files) from ZIP", templateName, count);
	}

	private int uploadDirectory(File dir, String storagePrefix, File baseDir) {
		int count = 0;
		File[] files = dir.listFiles();
		if (files == null) return 0;
		for (File file : files) {
			if (file.isDirectory()) {
				count += uploadDirectory(file, storagePrefix, baseDir);
			} else {
				String relativePath = baseDir.toPath().relativize(file.toPath()).toString().replace('\\', '/');
				String objectName = storagePrefix + relativePath;
				String contentType = storageService.guessContentType(objectName);
				try (FileInputStream fis = new FileInputStream(file)) {
					storageService.uploadStream(objectName, fis, file.length(), contentType);
					count++;
				} catch (Exception e) {
					log.warn("Failed to upload template file '{}': {}", objectName, e.getMessage());
				}
			}
		}
		return count;
	}

	public boolean hasContentFile(MultipartFile multipartFile) {
		File extractFolder = this.extractZipFile(multipartFile);
		if (extractFolder == null) {
			return false;
		}
		File workFolder = resolveExportFolder(extractFolder);
		boolean found = findContentFiles(workFolder).length > 0;
		try {
			cleanupDirectories(extractFolder, null);
		} catch (IOException e) {
			log.warn("Cleanup failed: {}", e.getMessage(), e);
		}
		return found;
	}

	private File resolveExportFolder(File extractFolder) {
		if (!(new File(extractFolder, EXPORT_FILE).exists())
				&& extractFolder.listFiles() != null
				&& Objects.requireNonNull(extractFolder.listFiles()).length == 1) {
			for (File fileOrDirectory : Objects.requireNonNull(extractFolder.listFiles())) {
				if (fileOrDirectory.isDirectory() && new File(fileOrDirectory, EXPORT_FILE).exists()) {
					return fileOrDirectory;
				}
			}
		}
		return extractFolder;
	}

	private File[] findContentFiles(File folder) {
		File[] files = folder.listFiles((dir, name) -> name.endsWith(CONTENT_SUFFIX));
		return files != null ? files : new File[0];
	}

	private void importContentFiles(File extractFolder, String taskId) {
		File[] contentFiles = findContentFiles(extractFolder);
		log.info("Found {} content file(s) in '{}'", contentFiles.length, extractFolder.getAbsolutePath());
		if (contentFiles.length == 0) {
			File[] allFiles = extractFolder.listFiles();
			if (allFiles != null) {
				for (File f : allFiles) {
					log.info("  File in extract folder: {} (dir={})", f.getName(), f.isDirectory());
				}
			}
		}
		ObjectMapper mapper = JsonMapper.builder().build();
		for (File contentFile : contentFiles) {
			log.info("Processing content file: {}", contentFile.getName());
			try (FileInputStream fis = new FileInputStream(contentFile)) {
				Map<String, Map<String, List<Map<String, Object>>>> contentMap =
						mapper.readValue(fis, new TypeReference<>() {});
				for (var siteEntry : contentMap.entrySet()) {
					var siteOpt = turSNSiteRepository.findByNameIgnoreCase(siteEntry.getKey());
					if (siteOpt.isEmpty()) {
						log.warn("Content references site '{}' but no matching SN Site found in database.",
								siteEntry.getKey());
						continue;
					}
					TurSNSite turSNSite = siteOpt.get();
					String effectiveTaskId = taskId != null ? taskId : turSNSite.getId();
					int indexed = contentExchangeService.importContent(
							turSNSite, Map.of(siteEntry.getKey(), siteEntry.getValue()),
							effectiveTaskId);
					log.info("Imported {} documents for site '{}'", indexed, siteEntry.getKey());
				}
			} catch (Exception e) {
				log.error("Error importing content from '{}': {}", contentFile.getName(), e.getMessage(), e);
			}
		}
	}

	private void importSNSiteFromExportFile(File extractFolder, File parentExtractFolder,
			boolean cleanup) {
		ObjectMapper mapper = JsonMapper.builder().build();

		try (FileInputStream fis = new FileInputStream(
				extractFolder.getAbsolutePath().concat(File.separator).concat(EXPORT_FILE))) {

			TurExchange turExchange = mapper.readValue(fis, TurExchange.class);
			processTurExchange(turExchange);
			if (cleanup) {
				cleanupDirectories(extractFolder, parentExtractFolder);
			}
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
