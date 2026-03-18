package com.viglet.turing.service.asset;

import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurMinioProperty;
import io.minio.*;
import io.minio.messages.Item;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for interacting with MinIO object storage.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Service
public class TurMinioService {
	private static final Logger log = LoggerFactory.getLogger(TurMinioService.class);
	private final TurConfigProperties configProperties;
	private MinioClient minioClient;

	public TurMinioService(TurConfigProperties configProperties) {
		this.configProperties = configProperties;
	}

	public boolean isEnabled() {
		TurMinioProperty minio = configProperties.getMinio();
		return minio != null && minio.isEnabled();
	}

	@PostConstruct
	void init() {
		TurMinioProperty minio = configProperties.getMinio();
		if (minio == null || !minio.isEnabled() || minio.getEndpoint() == null) {
			log.info("MinIO is disabled (turing.minio.enabled=false). Asset features are disabled.");
			return;
		}
		this.minioClient = MinioClient.builder()
				.endpoint(minio.getEndpoint())
				.credentials(minio.getAccessKey(), minio.getSecretKey())
				.build();
		ensureBucket(minio.getBucket());
	}

	public List<TurAssetItem> listObjects(String prefix) {
		if (minioClient == null) return List.of();
		String bucket = getBucket();
		String normalizedPrefix = (prefix == null || prefix.isBlank()) ? "" : prefix;
		List<TurAssetItem> items = new ArrayList<>();
		var builder = ListObjectsArgs.builder()
				.bucket(bucket)
				.prefix(normalizedPrefix)
				.recursive(false);
		Iterable<Result<Item>> results = minioClient.listObjects(builder.build());
		for (Result<Item> result : results) {
			try {
				Item item = result.get();
				String objectName = item.objectName();
				if (item.isDir()) {
					items.add(new TurAssetItem(objectName, 0, "", "", true));
				} else {
					ZonedDateTime lastModified = item.lastModified();
					items.add(new TurAssetItem(
							objectName,
							item.size(),
							guessContentType(objectName),
							lastModified != null ? lastModified.toString() : "",
							false));
				}
			} catch (Exception e) {
				log.error("Error listing MinIO object", e);
			}
		}
		return items;
	}

	public List<TurAssetItem> listAllObjects() {
		if (minioClient == null) return List.of();
		String bucket = getBucket();
		List<TurAssetItem> items = new ArrayList<>();
		var builder = ListObjectsArgs.builder()
				.bucket(bucket)
				.recursive(true);
		Iterable<Result<Item>> results = minioClient.listObjects(builder.build());
		for (Result<Item> result : results) {
			try {
				Item item = result.get();
				if (!item.isDir()) {
					ZonedDateTime lastModified = item.lastModified();
					items.add(new TurAssetItem(
							item.objectName(),
							item.size(),
							guessContentType(item.objectName()),
							lastModified != null ? lastModified.toString() : "",
							false));
				}
			} catch (Exception e) {
				log.error("Error listing MinIO object", e);
			}
		}
		return items;
	}

	public InputStream downloadObject(String objectName) {
		if (minioClient == null) throw new IllegalStateException("MinIO is not configured.");
		try {
			return minioClient.getObject(
					GetObjectArgs.builder()
							.bucket(getBucket())
							.object(objectName)
							.build());
		} catch (Exception e) {
			throw new RuntimeException("Failed to download object: " + objectName, e);
		}
	}

	public StatObjectResponse statObject(String objectName) {
		if (minioClient == null) throw new IllegalStateException("MinIO is not configured.");
		try {
			return minioClient.statObject(
					StatObjectArgs.builder()
							.bucket(getBucket())
							.object(objectName)
							.build());
		} catch (Exception e) {
			throw new RuntimeException("Failed to stat object: " + objectName, e);
		}
	}

	public void uploadObject(MultipartFile file, String prefix) {
		if (minioClient == null) throw new IllegalStateException("MinIO is not configured.");
		String objectName = (prefix != null && !prefix.isBlank() ? prefix : "") + file.getOriginalFilename();
		try (InputStream is = file.getInputStream()) {
			String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
			minioClient.putObject(
					PutObjectArgs.builder()
							.bucket(getBucket())
							.object(objectName)
							.stream(is, file.getSize(), -1)
							.contentType(contentType)
							.build());
		} catch (Exception e) {
			throw new RuntimeException("Failed to upload object: " + objectName, e);
		}
	}

	public void createFolder(String folderPath) {
		if (minioClient == null) throw new IllegalStateException("MinIO is not configured.");
		String path = folderPath.endsWith("/") ? folderPath : folderPath + "/";
		try {
			minioClient.putObject(
					PutObjectArgs.builder()
							.bucket(getBucket())
							.object(path)
							.stream(InputStream.nullInputStream(), 0, -1)
							.contentType("application/x-directory")
							.build());
		} catch (Exception e) {
			throw new RuntimeException("Failed to create folder: " + path, e);
		}
	}

	public void deleteObject(String objectName) {
		if (minioClient == null) throw new IllegalStateException("MinIO is not configured.");
		try {
			minioClient.removeObject(
					RemoveObjectArgs.builder()
							.bucket(getBucket())
							.object(objectName)
							.build());
		} catch (Exception e) {
			throw new RuntimeException("Failed to delete object: " + objectName, e);
		}
	}

	private String getBucket() {
		return configProperties.getMinio().getBucket();
	}

	private void ensureBucket(String bucket) {
		try {
			boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
			if (!exists) {
				minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
				log.info("Created MinIO bucket: {}", bucket);
			}
		} catch (Exception e) {
			log.error("Failed to ensure MinIO bucket '{}' exists", bucket, e);
		}
	}

	private String guessContentType(String name) {
		if (name == null) return "application/octet-stream";
		String lower = name.toLowerCase();
		if (lower.endsWith(".pdf")) return "application/pdf";
		if (lower.endsWith(".png")) return "image/png";
		if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
		if (lower.endsWith(".gif")) return "image/gif";
		if (lower.endsWith(".svg")) return "image/svg+xml";
		if (lower.endsWith(".txt")) return "text/plain";
		if (lower.endsWith(".csv")) return "text/csv";
		if (lower.endsWith(".json")) return "application/json";
		if (lower.endsWith(".xml")) return "application/xml";
		if (lower.endsWith(".zip")) return "application/zip";
		if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "application/msword";
		if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "application/vnd.ms-excel";
		return "application/octet-stream";
	}
}
