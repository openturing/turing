// TurSNSiteExport.java
package com.viglet.turing.exchange.sn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.service.storage.TurStorageService;
import com.viglet.turing.exchange.TurExchange;
import com.viglet.turing.exchange.sn.mixin.TurSNSiteExchangeMixin;
import com.viglet.turing.persistence.model.embedding.TurEmbeddingModel;
import com.viglet.turing.persistence.model.llm.TurLLMInstance;
import com.viglet.turing.persistence.model.se.TurSEInstance;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.genai.TurSNSiteGenAi;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.persistence.model.sn.merge.TurSNSiteMergeProviders;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingCondition;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSort;
import com.viglet.turing.persistence.model.sn.spotlight.TurSNSiteSpotlight;
import com.viglet.turing.persistence.model.store.TurStoreInstance;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Slf4j
@Service
public class TurSNSiteExportFileService {

	private static final String TMP_DIR = "store/tmp";
	private static final String EXPORT_JSON = "export.json";
	private static final String ZIP_EXTENSION = ".zip";

	public File getOrCreateTmpDir() {
		File userDir = Paths.get(System.getProperty("user.dir")).toFile();
		File tmpDir = Paths.get(userDir.getAbsolutePath(), TMP_DIR).toFile();
		try {
			Files.createDirectories(tmpDir.toPath());
		} catch (IOException e) {
			log.error("Could not create temp directory: {}", e.getMessage(), e);
			throw new IllegalStateException("Temp directory creation failed", e);
		}
		return tmpDir;
	}

	public File prepareExportFile(File tmpDir, String folderName, TurExchange turExchange) {
		File exportDir = Paths.get(tmpDir.getAbsolutePath(), folderName).toFile();
		File exportFile = new File(exportDir, EXPORT_JSON);
		try {
			// Ensure export directory exists
			if (!exportDir.exists() && !exportDir.mkdirs()) {
				throw new IOException("Failed to create export directory: " + exportDir.getAbsolutePath());
			}
			// Configure JsonMapper with mixins and pretty printing
			JsonMapper mapper = JsonMapper.builder()
					.configure(SerializationFeature.INDENT_OUTPUT, true)
					.addMixIn(TurSNRankingCondition.class, TurSNSiteExchangeMixin.class)
					.addMixIn(TurSNRankingExpression.class, TurSNSiteExchangeMixin.class)
					.addMixIn(TurSNSiteLocale.class, TurSNSiteExchangeMixin.class)
					.addMixIn(TurSNSiteSpotlight.class, TurSNSiteExchangeMixin.class)
					.addMixIn(TurSNSiteMergeProviders.class, TurSNSiteExchangeMixin.class)
					.addMixIn(TurSNSiteCustomSort.class, TurSNSiteExchangeMixin.class)
					.build();
			// Write export file atomically
			Path tempExportFile = Files.createTempFile(exportDir.toPath(), "export-", ".json");
			mapper.writer().writeValue(tempExportFile.toFile(), turExchange);
			Files.move(tempExportFile, exportFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			log.error("Error writing export file: {}", e.getMessage(), e);
			throw new IllegalStateException("Export file creation failed", e);
		}
		return exportFile;
	}

	public File createZipFile(File exportFile) {
		File zipFile = new File(exportFile.getParent() + ZIP_EXTENSION);
		try {
			TurCommonsUtils.addFilesToZip(exportFile.getParentFile(), zipFile);
		} catch (Exception e) {
			log.error("Error creating zip file: {}", e.getMessage(), e);
			throw new IllegalStateException("Zip file creation failed", e);
		}
		return zipFile;
	}

	public void writeZipToResponseAndCleanup(File zipFile, File exportFile, OutputStream output) {
		try {
			Path path = zipFile.toPath();
			byte[] data = Files.readAllBytes(path);
			output.write(data);
			output.flush();
		} catch (IOException e) {
			log.error("Error writing zip to response: {}", e.getMessage(), e);
		} finally {
			cleanup(exportFile, zipFile);
		}
	}

	private void cleanup(File exportFile, File zipFile) {
		try {
			FileUtils.deleteDirectory(exportFile.getParentFile());
			FileUtils.deleteQuietly(zipFile);
		} catch (Exception e) {
			log.warn("Cleanup failed: {}", e.getMessage(), e);
		}
	}

	public TurSNSiteExchange exportSNSite(TurSNSite turSNSite) {
		return new TurSNSiteExchange(turSNSite);
	}

	private final TurSNSiteExportValidator exportValidator;
	private final TurStorageService storageService;

	public TurSNSiteExportFileService(TurSNSiteExportValidator exportValidator,
									  TurStorageService storageService) {
		this.exportValidator = exportValidator;
		this.storageService = storageService;
	}

	public Path exportSNSitesToZip(List<TurSNSite> turSNSites,
			Map<String, Map<String, List<Map<String, Object>>>> contentMap,
			boolean includeTemplate) {
		File tmpDir = getOrCreateTmpDir();
		String folderName = "SNSite_" + System.currentTimeMillis();

		List<TurSNSiteExchange> shSiteExchanges = new ArrayList<>();
		Map<String, TurLLMInstance> llmInstances = new LinkedHashMap<>();
		Map<String, TurStoreInstance> storeInstances = new LinkedHashMap<>();
		Map<String, TurSEInstance> seInstances = new LinkedHashMap<>();
		Map<String, TurEmbeddingModel> embeddingModels = new LinkedHashMap<>();

		for (TurSNSite turSNSite : turSNSites) {
			shSiteExchanges.add(this.exportSNSite(turSNSite));
			collectReferences(turSNSite, llmInstances, storeInstances, seInstances, embeddingModels);
		}
		TurExchange turExchange = new TurExchange();
		turExchange.setSnSites(shSiteExchanges);
		turExchange.setLlm(new ArrayList<>(llmInstances.values()));
		turExchange.setStore(new ArrayList<>(storeInstances.values()));
		turExchange.setSe(new ArrayList<>(seInstances.values()));
		turExchange.setEmbeddingModels(new ArrayList<>(embeddingModels.values()));
		exportValidator.validate(turExchange);
		File exportFile = prepareExportFile(tmpDir, folderName, turExchange);

		if (contentMap != null && !contentMap.isEmpty()) {
			writeContentFile(exportFile.getParentFile(), contentMap);
		}

		if (includeTemplate) {
			for (TurSNSite turSNSite : turSNSites) {
				copySearchTemplateToExport(turSNSite, exportFile.getParentFile());
			}
		}

		File zipFile = createZipFile(exportFile);
		return zipFile.toPath();
	}

	private void writeContentFile(File exportDir,
			Map<String, Map<String, List<Map<String, Object>>>> contentMap) {
		for (var entry : contentMap.entrySet()) {
			String siteName = entry.getKey().replaceAll("[^a-zA-Z0-9_-]", "_");
			File contentFile = new File(exportDir, siteName + "_content.json");
			try {
				JsonMapper mapper = JsonMapper.builder()
						.configure(SerializationFeature.INDENT_OUTPUT, true)
						.build();
				mapper.writer().writeValue(contentFile, Map.of(entry.getKey(), entry.getValue()));
			} catch (Exception e) {
				log.error("Error writing content file for site '{}': {}", entry.getKey(), e.getMessage(), e);
			}
		}
	}

	private void copySearchTemplateToExport(TurSNSite turSNSite, File exportDir) {
		String template = turSNSite.getSearchTemplate();
		if (template == null || template.isBlank() || !storageService.isEnabled()) {
			return;
		}
		String prefix = "public/" + template + "/";
		List<TurAssetItem> files = storageService.listAllObjects().stream()
				.filter(item -> !item.directory() && item.name().startsWith(prefix))
				.toList();
		if (files.isEmpty()) {
			log.debug("No SPA template files found for template '{}'", template);
			return;
		}
		File appDir = new File(exportDir, "app");
		for (TurAssetItem file : files) {
			String relativePath = file.name().substring(prefix.length());
			File targetFile = new File(appDir, relativePath);
			try {
				Files.createDirectories(targetFile.getParentFile().toPath());
				try (InputStream is = storageService.downloadObject(file.name())) {
					Files.copy(is, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (Exception e) {
				log.warn("Failed to copy template file '{}': {}", file.name(), e.getMessage());
			}
		}
		log.info("Exported SPA template '{}' ({} files) to export zip", template, files.size());
	}

	private void collectReferences(TurSNSite turSNSite,
			Map<String, TurLLMInstance> llmInstances,
			Map<String, TurStoreInstance> storeInstances,
			Map<String, TurSEInstance> seInstances,
			Map<String, TurEmbeddingModel> embeddingModels) {
		if (turSNSite.getTurSEInstance() != null && turSNSite.getTurSEInstance().getId() != null) {
			seInstances.putIfAbsent(turSNSite.getTurSEInstance().getId(), turSNSite.getTurSEInstance());
		}

		TurSNSiteGenAi genAi = turSNSite.getTurSNSiteGenAi();
		if (genAi == null || genAi.getTurAIAgent() == null) {
			return;
		}
		var agent = genAi.getTurAIAgent();

		// Pull the referenced LLM/store/embedding from the agent so the export
		// bundle is self-contained even after the per-site fields were removed.
		if (agent.getLlmInstances() != null) {
			agent.getLlmInstances().forEach(llm -> {
				if (llm != null && llm.getId() != null) {
					llmInstances.putIfAbsent(llm.getId(), llm);
				}
			});
		}
		if (agent.getTurStoreInstance() != null && agent.getTurStoreInstance().getId() != null) {
			storeInstances.putIfAbsent(agent.getTurStoreInstance().getId(), agent.getTurStoreInstance());
		}
		if (agent.getTurEmbeddingModelInstance() != null && agent.getTurEmbeddingModelInstance().getId() != null) {
			embeddingModels.putIfAbsent(agent.getTurEmbeddingModelInstance().getId(),
					agent.getTurEmbeddingModelInstance());
		}
	}
}
