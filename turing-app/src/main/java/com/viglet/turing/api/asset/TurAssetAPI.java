package com.viglet.turing.api.asset;

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

	public TurAssetAPI(TurMinioService turMinioService) {
		this.turMinioService = turMinioService;
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

	private String extractFileName(String objectName) {
		int lastSlash = objectName.lastIndexOf('/');
		return lastSlash >= 0 ? objectName.substring(lastSlash + 1) : objectName;
	}
}
