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

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import java.util.Objects;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.exchange.agent.TurAIAgentImportService;
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

    // --- S1192: extracted duplicated literals ---
    private static final String CLEANUP_FAILED = "Cleanup failed: {}";


	private final TurSNSiteImport turSNSiteImport;
	private final TurSNSiteContentExchangeService contentExchangeService;
	private final TurSNSiteRepository turSNSiteRepository;
	private final TurStorageService storageService;
	private final TurAIAgentImportService turAIAgentImportService;
	private static final String EXPORT_FILE = "export.json";
	private static final String CONTENT_SUFFIX = "_content.json";
	private static final String APP_DIR = "app";
	private static final String TURING_MANIFEST = "turing-manifest.json";
	private static final String PAGES_PREFIX = "public/";

	public record ImportResult(boolean siteImported, String siteName,
							   int contentDocuments, int contentFiles,
							   boolean templateImported, String templateName, int templateFiles,
							   String error,
							   List<SiteConflict> conflicts,
							   boolean searchEngineAvailable,
							   AgentImportSummary agentSummary,
							   List<AgentConflict> agentConflicts) {

		public static ImportResult error(String error) {
			return new ImportResult(false, null, 0, 0, false, null, 0, error,
					List.of(), true, null, List.of());
		}

		public static ImportResult conflict(List<SiteConflict> conflicts) {
			return new ImportResult(false, null, 0, 0, false, null, 0, null,
					conflicts, true, null, List.of());
		}
	}

	/**
	 * A site present in the import bundle that already exists in the database
	 * (matched by ID). The frontend uses this to prompt the user before overwriting.
	 *
	 * @since 2026.2.4
	 */
	public record SiteConflict(String id, String name) {
	}

	/**
	 * An AI agent present in the import bundle that already exists in the
	 * database (matched by ID). Mirrors {@link SiteConflict} so the same
	 * overwrite-toggle UX applies to both site and agent imports.
	 *
	 * @since 2026.2.8
	 */
	public record AgentConflict(String id, String name) {
	}

	/**
	 * Summary of what the AI-agent block of an import bundle produced. Null
	 * on the result when the bundle didn't carry any {@code agents[]}.
	 *
	 * @since 2026.2.8
	 */
	public record AgentImportSummary(List<String> imported, List<String> skipped,
									 int personasResolved, int llmInstancesResolved,
									 int mcpServersResolved, int customToolsResolved,
									 int chatFlowsImported, String error) {
	}

	/**
	 * Outcome of {@code importContentFiles}: how many files were processed, how
	 * many documents were committed, and whether the search engine was reachable.
	 * {@code searchEngineAvailable=false} indicates the SE circuit breaker
	 * tripped (or the SE was otherwise down) and content was not indexed.
	 */
	private record ContentImportResult(int files, int documents, boolean searchEngineAvailable) {
	}

	public TurImportExchange(TurSNSiteImport turSNSiteImport,
			TurSNSiteContentExchangeService contentExchangeService,
			TurSNSiteRepository turSNSiteRepository,
			TurStorageService storageService,
			TurAIAgentImportService turAIAgentImportService) {
		this.turSNSiteImport = turSNSiteImport;
		this.contentExchangeService = contentExchangeService;
		this.turSNSiteRepository = turSNSiteRepository;
		this.storageService = storageService;
		this.turAIAgentImportService = turAIAgentImportService;
	}

	public ImportResult importFromMultipartFile(MultipartFile multipartFile) {
		return importFromMultipartFile(multipartFile, false, false, null, true);
	}

	public ImportResult importFromMultipartFile(MultipartFile multipartFile,
			boolean includeContent, boolean includeTemplate, String taskId) {
		return importFromMultipartFile(multipartFile, includeContent, includeTemplate, taskId, true);
	}

	public ImportResult importFromMultipartFile(MultipartFile multipartFile,
			boolean includeContent, boolean includeTemplate, String taskId, boolean overwrite) {
		File rawExtractFolder = this.extractZipFile(multipartFile);
		if (rawExtractFolder == null) {
			return ImportResult.error("Failed to extract ZIP file");
		}
		ExtractRoots roots = unwrapSingleSubdirectory(rawExtractFolder);
		File extractFolder = roots.extractFolder();
		File parentExtractFolder = roots.parentExtractFolder();

		// export.json is mandatory
		File exportFile = new File(extractFolder, EXPORT_FILE);
		if (!exportFile.isFile()) {
			cleanupSafely(extractFolder, parentExtractFolder);
			return ImportResult.error("Missing required export.json in ZIP");
		}

		// When overwrite=false and the bundle would replace an existing site by ID,
		// skip the structural import but still process content/template so the user
		// can re-index content or re-deploy the template against the existing site.
		List<SiteConflict> conflicts = detectConflicts(extractFolder);
		boolean skipStructure = !overwrite && !conflicts.isEmpty();

		String siteName;
		boolean siteImported;
		if (skipStructure) {
			siteName = conflicts.getFirst().name();
			siteImported = false;
			log.info("Skipping structural import for {} existing site(s); overwrite=false.",
					conflicts.size());
		} else {
			siteName = importSNSiteFromExportFile(extractFolder);
			siteImported = siteName != null;
			if (!siteImported) {
				cleanupSafely(extractFolder, parentExtractFolder);
				return ImportResult.error("Failed to import SN Site from export.json");
			}
		}

		// content is optional
		int contentDocuments = 0;
		int contentFiles = 0;
		boolean searchEngineAvailable = true;
		if (includeContent) {
			ContentImportResult contentResult = importContentFiles(extractFolder, taskId);
			contentFiles = contentResult.files();
			contentDocuments = contentResult.documents();
			searchEngineAvailable = contentResult.searchEngineAvailable();
		}

		// template is optional
		boolean templateImported = false;
		String templateName = null;
		int templateFileCount = 0;
		if (includeTemplate) {
			templateFileCount = importTemplateFromAppFolder(extractFolder);
			templateImported = templateFileCount > 0;
			if (templateImported) {
				templateName = readTemplateName(extractFolder);
			}
		}

		// AI agent block is optional — same envelope, side-by-side with snSites.
		// Reads chat-flow + Groovy sibling files from extractFolder before cleanup.
		AgentImportSummary agentSummary = importAgentsFromExportFile(extractFolder, overwrite);

		cleanupSafely(extractFolder, parentExtractFolder);

		log.info("Import complete — site: '{}', content: {} docs from {} files, template: '{}'  ({} files), agents imported: {}, skipped: {}",
				siteName, contentDocuments, contentFiles,
				templateName != null ? templateName : "none", templateFileCount,
				agentSummary == null ? 0 : agentSummary.imported().size(),
				agentSummary == null ? 0 : agentSummary.skipped().size());

		return new ImportResult(siteImported, siteName,
				contentDocuments, contentFiles,
				templateImported, templateName, templateFileCount,
				null, conflicts, searchEngineAvailable,
				agentSummary, List.of());
	}

	/** The resolved export root and (optional) wrapper directory above it. */
	private record ExtractRoots(File extractFolder, File parentExtractFolder) {
	}

	/**
	 * When the ZIP wrapped everything in a single top-level directory (no
	 * {@code export.json} at the root), descends into that directory and reports
	 * the original as the parent (for cleanup). Otherwise returns the root as-is.
	 */
	private ExtractRoots unwrapSingleSubdirectory(File extractFolder) {
		if (!(new File(extractFolder, EXPORT_FILE).exists())
				&& (Objects.requireNonNull(extractFolder.listFiles()).length == 1)) {
			for (File fileOrDirectory : Objects.requireNonNull(extractFolder.listFiles())) {
				if (fileOrDirectory.isDirectory() && new File(fileOrDirectory, EXPORT_FILE).exists()) {
					return new ExtractRoots(fileOrDirectory, extractFolder);
				}
			}
		}
		return new ExtractRoots(extractFolder, null);
	}

	private AgentImportSummary importAgentsFromExportFile(File extractFolder, boolean overwrite) {
		File exportFile = new File(extractFolder, EXPORT_FILE);
		if (!exportFile.isFile()) return null;
		try (FileInputStream fis = new FileInputStream(exportFile)) {
			ObjectMapper mapper = JsonMapper.builder().build();
			TurExchange exchange = mapper.readValue(fis, TurExchange.class);
			if (exchange.getAgents() == null || exchange.getAgents().isEmpty()) {
				return null;
			}
			var result = turAIAgentImportService.importAgents(exchange, extractFolder, overwrite);
			return new AgentImportSummary(result.importedAgentNames(), result.skippedAgentNames(),
					result.personasResolved(), result.llmInstancesResolved(),
					result.mcpServersResolved(), result.customToolsResolved(),
					result.chatFlowsImported(), result.error());
		} catch (Exception e) {
			log.error("Error importing AI agents: {}", e.getMessage(), e);
			return new AgentImportSummary(List.of(), List.of(), 0, 0, 0, 0, 0, e.getMessage());
		}
	}

	/**
	 * Parses the export.json inside {@code extractFolder} and returns the subset of
	 * sites that already exist in the database (matched by ID). Empty means no conflict.
	 *
	 * @since 2026.2.4
	 */
	private List<SiteConflict> detectConflicts(File extractFolder) {
		File exportFile = new File(extractFolder, EXPORT_FILE);
		if (!exportFile.isFile()) {
			return List.of();
		}
		ObjectMapper mapper = JsonMapper.builder().build();
		try (FileInputStream fis = new FileInputStream(exportFile)) {
			TurExchange exchange = mapper.readValue(fis, TurExchange.class);
			if (exchange.getSnSites() == null) {
				return List.of();
			}
			return exchange.getSnSites().stream()
					.filter(site -> site.getId() != null
							&& turSNSiteRepository.findById(site.getId()).isPresent())
					.map(site -> new SiteConflict(site.getId(), site.getName()))
					.toList();
		} catch (Exception e) {
			log.warn("Failed to inspect export.json for conflicts: {}", e.getMessage());
			return List.of();
		}
	}

	public ImportResult importFromMultipartFile(MultipartFile multipartFile,
			boolean includeContent, String taskId) {
		return importFromMultipartFile(multipartFile, includeContent, false, taskId);
	}

	/**
	 * Checks the ZIP for available import options: content files, valid SPA template,
	 * existing SN sites that would be overwritten (matched by ID), and existing AI
	 * agents that would be overwritten.
	 */
	public Map<String, Object> checkZip(MultipartFile multipartFile) {
		File extractFolder = this.extractZipFile(multipartFile);
		if (extractFolder == null) {
			return Map.of("hasContent", false, "hasTemplate", false,
					"hasAgents", false,
					"conflicts", List.of(), "agentConflicts", List.of());
		}
		File workFolder = resolveExportFolder(extractFolder);
		boolean hasContent = findContentFiles(workFolder).length > 0;
		boolean hasTemplate = hasValidTemplate(workFolder);
		String templateName = null;
		if (hasTemplate) {
			templateName = readTemplateName(workFolder);
		}
		List<SiteConflict> conflicts = detectConflicts(workFolder);
		AgentDetectionResult agentDetection = detectAgents(workFolder);
		try {
			cleanupDirectories(extractFolder, null);
		} catch (IOException e) {
			log.warn(CLEANUP_FAILED, e.getMessage(), e);
		}
		Map<String, Object> result = new java.util.HashMap<>();
		result.put("hasContent", hasContent);
		result.put("hasTemplate", hasTemplate);
		result.put("templateName", templateName);
		result.put("conflicts", conflicts);
		result.put("hasAgents", agentDetection.hasAgents());
		result.put("agentConflicts", agentDetection.conflicts());
		result.put("agentTitles", agentDetection.titles());
		return result;
	}

	private record AgentDetectionResult(boolean hasAgents, List<String> titles,
										List<AgentConflict> conflicts) {
	}

	/**
	 * Reads the {@code agents[]} block inside the bundle's {@code export.json}
	 * and reports the titles found + which of them collide with an existing
	 * agent in the database (matched by ID). Mirrors {@link #detectConflicts}
	 * for SN sites.
	 */
	private AgentDetectionResult detectAgents(File extractFolder) {
		File exportFile = new File(extractFolder, EXPORT_FILE);
		if (!exportFile.isFile()) {
			return new AgentDetectionResult(false, List.of(), List.of());
		}
		try (FileInputStream fis = new FileInputStream(exportFile)) {
			ObjectMapper mapper = JsonMapper.builder().build();
			TurExchange exchange = mapper.readValue(fis, TurExchange.class);
			if (exchange.getAgents() == null || exchange.getAgents().isEmpty()) {
				return new AgentDetectionResult(false, List.of(), List.of());
			}
			List<String> titles = exchange.getAgents().stream()
					.map(a -> a.getTitle() == null ? "(untitled)" : a.getTitle())
					.toList();
			List<AgentConflict> conflicts = turAIAgentImportService.detectConflicts(exchange).stream()
					.map(c -> new AgentConflict(c.id(), c.name()))
					.toList();
			return new AgentDetectionResult(true, titles, conflicts);
		} catch (Exception e) {
			log.warn("Failed to inspect export.json for agents: {}", e.getMessage());
			return new AgentDetectionResult(false, List.of(), List.of());
		}
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

	/**
	 * @return {filesUploaded} — 0 if skipped
	 */
	private int importTemplateFromAppFolder(File extractFolder) {
		File appDir = new File(extractFolder, APP_DIR);
		if (!appDir.isDirectory() || !storageService.isEnabled()) {
			log.debug("Skipping template import: app/ dir missing or storage disabled");
			return 0;
		}
		File manifest = new File(appDir, TURING_MANIFEST);
		if (!manifest.isFile()) {
			log.debug("Skipping template import: no valid turing-manifest.json");
			return 0;
		}
		String templateName = readTemplateName(extractFolder);
		if (templateName == null || templateName.isBlank()) {
			log.warn("Skipping template import: turing-manifest.json has no name");
			return 0;
		}
		String prefix = PAGES_PREFIX + templateName + "/";
		storageService.deleteObjectsWithPrefix(prefix);
		int count = uploadDirectory(appDir, prefix, appDir);
		log.info("Imported SPA template '{}' ({} files) from ZIP", templateName, count);
		return count;
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
			log.warn(CLEANUP_FAILED, e.getMessage(), e);
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

	/**
	 * @return filesProcessed, totalDocuments and whether the search engine was reachable
	 */
	private ContentImportResult importContentFiles(File extractFolder, String taskId) {
		File[] contentFiles = findContentFiles(extractFolder);
		log.info("Found {} content file(s) in '{}'", contentFiles.length, extractFolder.getAbsolutePath());
		if (contentFiles.length == 0) {
			return new ContentImportResult(0, 0, true);
		}
		int totalDocuments = 0;
		boolean searchEngineAvailable = true;
		ObjectMapper mapper = JsonMapper.builder().build();
		for (File contentFile : contentFiles) {
			log.info("Processing content file: {}", contentFile.getName());
			try (FileInputStream fis = new FileInputStream(contentFile)) {
				Map<String, Map<String, List<Map<String, Object>>>> contentMap =
						mapper.readValue(fis, new TypeReference<>() {});
				for (var siteEntry : contentMap.entrySet()) {
					totalDocuments += importSiteContent(siteEntry, taskId);
				}
			} catch (CallNotPermittedException e) {
				searchEngineAvailable = false;
				log.warn("Search engine unavailable while importing content from '{}': {}",
						contentFile.getName(), e.getMessage());
			} catch (Exception e) {
				if (isSearchEngineUnavailable(e)) {
					searchEngineAvailable = false;
					log.warn("Search engine unavailable while importing content from '{}': {}",
							contentFile.getName(), e.getMessage());
					continue;
				}
				log.error("Error importing content from '{}': {}", contentFile.getName(), e.getMessage(), e);
			}
		}
		return new ContentImportResult(contentFiles.length, totalDocuments, searchEngineAvailable);
	}

	/**
	 * Imports the content for a single site entry. Returns the number of
	 * documents indexed, or 0 when the referenced site is not found.
	 */
	private int importSiteContent(
			Map.Entry<String, Map<String, List<Map<String, Object>>>> siteEntry, String taskId) {
		var siteOpt = turSNSiteRepository.findByNameIgnoreCase(siteEntry.getKey());
		if (siteOpt.isEmpty()) {
			log.warn("Content references site '{}' but no matching SN Site found in database.",
					siteEntry.getKey());
			return 0;
		}
		TurSNSite turSNSite = siteOpt.get();
		String effectiveTaskId = taskId != null ? taskId : turSNSite.getId();
		int indexed = contentExchangeService.importContent(
				turSNSite, Map.of(siteEntry.getKey(), siteEntry.getValue()), effectiveTaskId);
		log.info("Imported {} documents for site '{}'", indexed, siteEntry.getKey());
		return indexed;
	}

	/**
	 * Returns true when {@code e} is — or wraps — a Resilience4j circuit-breaker
	 * trip. Used to log SE-unavailable cases as WARN (transient/external) rather
	 * than ERROR (application bug).
	 */
	private static boolean isSearchEngineUnavailable(Throwable e) {
		for (Throwable cursor = e; cursor != null; cursor = cursor.getCause()) {
			if (cursor instanceof CallNotPermittedException) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return the imported site name, or null on failure
	 */
	private String importSNSiteFromExportFile(File extractFolder) {
		ObjectMapper mapper = JsonMapper.builder().build();

		try (FileInputStream fis = new FileInputStream(
				extractFolder.getAbsolutePath().concat(File.separator).concat(EXPORT_FILE))) {

			TurExchange turExchange = mapper.readValue(fis, TurExchange.class);
			processTurExchange(turExchange);
			if (turExchange.getSnSites() != null && !turExchange.getSnSites().isEmpty()) {
				return turExchange.getSnSites().getFirst().getName();
			}
			return "unknown";
		} catch (Exception e) {
			log.error("Error importing SN Site: {}", e.getMessage(), e);
			return null;
		}
	}

	private void cleanupSafely(File extractFolder, File parentExtractFolder) {
		try {
			cleanupDirectories(extractFolder, parentExtractFolder);
		} catch (IOException e) {
			log.warn(CLEANUP_FAILED, e.getMessage(), e);
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
				log.warn(CLEANUP_FAILED, e.getMessage(), e);
			}
		}
	}

	public ImportResult importFromFile(File file) {
		try (FileInputStream input = new FileInputStream(file)) {
			MultipartFile multipartFile = new MockMultipartFile(file.getName(), IOUtils.toByteArray(input));
			return this.importFromMultipartFile(multipartFile);
		} catch (IOException | IllegalStateException e) {
			log.error(e.getMessage(), e);
		}
		return ImportResult.error("Failed to read file: " + file.getName());
	}

	public File extractZipFile(MultipartFile file) {
		return TurSpringUtils.extractZipFile(file);
	}
}
