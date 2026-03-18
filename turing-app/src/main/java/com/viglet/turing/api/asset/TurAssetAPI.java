package com.viglet.turing.api.asset;

import com.viglet.turing.persistence.repository.asset.TurAssetTrainingRecordRepository;
import com.viglet.turing.service.asset.TurAssetTrainingService;
import com.viglet.turing.service.asset.TurAssetTrainingState;
import com.viglet.turing.service.asset.TurAssetTrainingStatus;
import com.viglet.turing.service.asset.TurMinioService;
import io.minio.StatObjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

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
	private final TurMinioService turMinioService;
	private final TurAssetTrainingService turAssetTrainingService;

	public TurAssetAPI(TurMinioService turMinioService, TurAssetTrainingService turAssetTrainingService) {
		this.turMinioService = turMinioService;
		this.turAssetTrainingService = turAssetTrainingService;
	}

	@Operation(summary = "List assets in a folder")
	@GetMapping
	public List<TurAssetItem> list(@RequestParam(defaultValue = "") String prefix) {
		return turMinioService.listObjects(prefix);
	}

	@Operation(summary = "Download an asset")
	@GetMapping("/download")
	public ResponseEntity<InputStreamResource> download(@RequestParam String objectName) {
		StatObjectResponse stat = turMinioService.statObject(objectName);
		InputStream stream = turMinioService.downloadObject(objectName);
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + extractFileName(objectName) + "\"")
				.contentType(MediaType.parseMediaType(stat.contentType()))
				.contentLength(stat.size())
				.body(new InputStreamResource(stream));
	}

	@Operation(summary = "Preview an asset inline (no download header)")
	@GetMapping("/preview")
	public ResponseEntity<InputStreamResource> preview(@RequestParam String objectName) {
		StatObjectResponse stat = turMinioService.statObject(objectName);
		InputStream stream = turMinioService.downloadObject(objectName);
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
		StatObjectResponse stat = turMinioService.statObject(objectName);
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
			turMinioService.uploadObject(file, prefix);
		}
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "Create a folder")
	@PostMapping("/folder")
	public ResponseEntity<Void> createFolder(@RequestParam String path) {
		turMinioService.createFolder(path);
		return ResponseEntity.ok().build();
	}

	@Operation(summary = "Delete an asset")
	@DeleteMapping
	public ResponseEntity<Void> delete(@RequestParam String objectName) {
		turMinioService.deleteObject(objectName);
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
