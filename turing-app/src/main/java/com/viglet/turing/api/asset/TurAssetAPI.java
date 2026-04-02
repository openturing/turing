package com.viglet.turing.api.asset;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.viglet.turing.service.asset.TurAssetEvent;
import com.viglet.turing.service.asset.TurAssetTrainingService;
import com.viglet.turing.service.asset.TurAssetTrainingState;
import com.viglet.turing.service.asset.TurAssetTrainingStatus;
import com.viglet.turing.service.storage.TurStorageObjectStat;
import com.viglet.turing.service.storage.TurStorageService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST API for managing assets stored in MinIO.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@RestController
@RequestMapping("/api/asset")
@Tag(name = "Assets", description = "Asset Management API (MinIO)")
public class TurAssetAPI {
	private final TurStorageService storageService;
	private final TurAssetTrainingService turAssetTrainingService;
	private final org.springframework.context.ApplicationEventPublisher eventPublisher;

	public TurAssetAPI(TurStorageService storageService, TurAssetTrainingService turAssetTrainingService,
			org.springframework.context.ApplicationEventPublisher eventPublisher) {
		this.storageService = storageService;
		this.turAssetTrainingService = turAssetTrainingService;
		this.eventPublisher = eventPublisher;
	}

	@Operation(summary = "List assets in a folder")
	@GetMapping
	public List<TurAssetItem> list(@RequestParam(defaultValue = "") String prefix) {
		return storageService.listObjects(prefix);
	}

	@Operation(summary = "Download an asset")
	@GetMapping("/download")
	public ResponseEntity<InputStreamResource> download(@RequestParam String objectName) {
		TurStorageObjectStat stat = storageService.statObject(objectName);
		InputStream stream = storageService.downloadObject(objectName);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + extractFileName(objectName) + "\"")
				.contentType(MediaType.parseMediaType(stat.contentType()))
				.contentLength(stat.size())
				.body(new InputStreamResource(stream));
	}

	@Operation(summary = "Preview an asset inline (no download header)")
	@GetMapping("/preview")
	public ResponseEntity<InputStreamResource> preview(@RequestParam String objectName) {
		TurStorageObjectStat stat = storageService.statObject(objectName);
		InputStream stream = storageService.downloadObject(objectName);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + extractFileName(objectName) + "\"")
				.header("X-Frame-Options", "ALLOWALL")
				.header("Content-Security-Policy", "frame-ancestors *")
				.contentType(MediaType.parseMediaType(stat.contentType()))
				.contentLength(stat.size())
				.body(new InputStreamResource(stream));
	}

	@Operation(summary = "Get asset metadata")
	@GetMapping("/metadata")
	public TurAssetItem metadata(@RequestParam String objectName) {
		TurStorageObjectStat stat = storageService.statObject(objectName);
		return new TurAssetItem(
				objectName,
				stat.size(),
				stat.contentType(),
				stat.lastModified() != null ? stat.lastModified().toString() : "",
				false);
	}

	@Operation(summary = "Upload assets")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<Void> upload(@RequestParam("files") List<MultipartFile> files,
			@RequestParam(defaultValue = "") String prefix) {
		for (MultipartFile file : files) {
			storageService.uploadObject(file, prefix);
			String objectName = (prefix != null && !prefix.isBlank() ? prefix : "") + file.getOriginalFilename();
			String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
			eventPublisher.publishEvent(TurAssetEvent.uploaded(objectName, contentType, file.getSize()));
		}
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "Create a folder")
	@PostMapping("/folder")
	public ResponseEntity<Void> createFolder(@RequestParam String path) {
		storageService.createFolder(path);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "Delete an asset")
	@DeleteMapping
	public ResponseEntity<Void> delete(@RequestParam String objectName) {
		storageService.deleteObject(objectName);
		eventPublisher.publishEvent(TurAssetEvent.deleted(objectName));
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "Start AI training from assets")
	@PostMapping("/train")
	public ResponseEntity<TurAssetTrainingStatus> startTraining() {
		TurAssetTrainingStatus result = turAssetTrainingService.startTraining();
		if (result.state() == TurAssetTrainingState.RUNNING) {
			return ResponseEntity.accepted().body(result);
		}
		return ResponseEntity.status(org.springframework.http.HttpStatus.CONFLICT).body(result);
	}

	@Operation(summary = "Get AI training status")
	@GetMapping("/train/status")
	public TurAssetTrainingStatus getTrainingStatus() {
		return turAssetTrainingService.getStatus();
	}

	@Operation(summary = "Get training status for a list of assets")
	@GetMapping("/train/records")
	public Map<String, String> getTrainingRecords(@RequestParam List<String> objectNames) {
		return turAssetTrainingService.getTrainedAtMap(objectNames);
	}

	private String extractFileName(String objectName) {
		int lastSlash = objectName.lastIndexOf('/');
		return lastSlash >= 0 ? objectName.substring(lastSlash + 1) : objectName;
	}
}
