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
import java.util.Map;

/**
 * Service for interacting with MinIO object storage.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Service
public class TurMinioService {
	private static final Logger log = LoggerFactory.getLogger(TurMinioService.class);
	private static final String PATH_DELIMITER = "/";
	private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
	private static final Map<String, String> EXTENSION_TO_CONTENT_TYPE = Map.ofEntries(
			Map.entry(".pdf", "application/pdf"),
			Map.entry(".png", "image/png"),
			Map.entry(".jpg", "image/jpeg"),
			Map.entry(".jpeg", "image/jpeg"),
			Map.entry(".gif", "image/gif"),
			Map.entry(".svg", "image/svg+xml"),
			Map.entry(".txt", "text/plain"),
			Map.entry(".csv", "text/csv"),
			Map.entry(".json", "application/json"),
			Map.entry(".xml", "application/xml"),
			Map.entry(".zip", "application/zip"),
			Map.entry(".doc", "application/msword"),
			Map.entry(".docx", "application/msword"),
			Map.entry(".xls", "application/vnd.ms-excel"),
			Map.entry(".xlsx", "application/vnd.ms-excel")
	);
	private static final String MINIO_NOT_CONFIGURED = "MinIO is not configured.";
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
		requireEnabled();
		try {
			return minioClient.getObject(
					GetObjectArgs.builder()
							.bucket(getBucket())
							.object(objectName)
							.build());
		} catch (Exception e) {
			throw new IllegalStateException("Failed to download object: " + objectName, e);
		}
	}

	public StatObjectResponse statObject(String objectName) {
		requireEnabled();
		try {
			return minioClient.statObject(
					StatObjectArgs.builder()
							.bucket(getBucket())
							.object(objectName)
							.build());
		} catch (Exception e) {
			throw new IllegalStateException("Failed to stat object: " + objectName, e);
		}
	}

	public void uploadObject(MultipartFile file, String prefix) {
		requireEnabled();
		String objectName = (prefix != null && !prefix.isBlank() ? prefix : "") + file.getOriginalFilename();
		try (InputStream is = file.getInputStream()) {
			String contentType = file.getContentType() != null ? file.getContentType() : DEFAULT_CONTENT_TYPE;
			minioClient.putObject(
					PutObjectArgs.builder()
							.bucket(getBucket())
							.object(objectName)
							.stream(is, file.getSize(), -1L)
							.contentType(contentType)
							.build());
		} catch (Exception e) {
			throw new IllegalStateException("Failed to upload object: " + objectName, e);
		}
	}

	public void createFolder(String folderPath) {
		requireEnabled();
		String path = folderPath.endsWith(PATH_DELIMITER) ? folderPath : folderPath + PATH_DELIMITER;
		try {
			minioClient.putObject(
					PutObjectArgs.builder()
							.bucket(getBucket())
							.object(path)
							.stream(InputStream.nullInputStream(), 0L, -1L)
							.contentType("application/x-directory")
							.build());
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create folder: " + path, e);
		}
	}

	public void deleteObject(String objectName) {
		requireEnabled();
		try {
			minioClient.removeObject(
					RemoveObjectArgs.builder()
							.bucket(getBucket())
							.object(objectName)
							.build());
		} catch (Exception e) {
			throw new IllegalStateException("Failed to delete object: " + objectName, e);
		}
	}

	private void requireEnabled() {
		if (minioClient == null) throw new IllegalStateException(MINIO_NOT_CONFIGURED);
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

	String guessContentType(String name) {
		if (name == null) return DEFAULT_CONTENT_TYPE;
		String lower = name.toLowerCase();
		return EXTENSION_TO_CONTENT_TYPE.entrySet().stream()
				.filter(entry -> lower.endsWith(entry.getKey()))
				.map(Map.Entry::getValue)
				.findFirst()
				.orElse(DEFAULT_CONTENT_TYPE);
	}
}
