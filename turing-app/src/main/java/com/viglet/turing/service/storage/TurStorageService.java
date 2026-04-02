package com.viglet.turing.service.storage;

import com.viglet.turing.api.asset.TurAssetItem;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * Abstraction for object storage operations (MinIO, filesystem, or no-op).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public interface TurStorageService {

    TurStorageType getType();

    boolean isEnabled();

    List<TurAssetItem> listObjects(String prefix);

    List<TurAssetItem> listAllObjects();

    InputStream downloadObject(String objectName);

    TurStorageObjectStat statObject(String objectName);

    void uploadObject(MultipartFile file, String prefix);

    void createFolder(String folderPath);

    void uploadStream(String objectName, InputStream inputStream, long size, String contentType);

    void deleteObjectsWithPrefix(String prefix);

    void deleteObject(String objectName);

    default String guessContentType(String name) {
        return TurStorageContentTypes.guessContentType(name);
    }
}
