package com.viglet.turing.service.storage;

import com.viglet.turing.api.asset.TurAssetItem;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * No-op storage implementation used when storage is disabled ({@code turing.storage.type=none}).
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public class TurNoOpStorageService implements TurStorageService {

    private static final String STORAGE_NOT_CONFIGURED = "Storage is not configured.";

    @Override
    public TurStorageType getType() {
        return TurStorageType.NONE;
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public List<TurAssetItem> listObjects(String prefix) {
        return List.of();
    }

    @Override
    public List<TurAssetItem> listAllObjects() {
        return List.of();
    }

    @Override
    public InputStream downloadObject(String objectName) {
        throw new IllegalStateException(STORAGE_NOT_CONFIGURED);
    }

    @Override
    public TurStorageObjectStat statObject(String objectName) {
        throw new IllegalStateException(STORAGE_NOT_CONFIGURED);
    }

    @Override
    public void uploadObject(MultipartFile file, String prefix) {
        throw new IllegalStateException(STORAGE_NOT_CONFIGURED);
    }

    @Override
    public void createFolder(String folderPath) {
        throw new IllegalStateException(STORAGE_NOT_CONFIGURED);
    }

    @Override
    public void uploadStream(String objectName, InputStream inputStream, long size, String contentType) {
        throw new IllegalStateException(STORAGE_NOT_CONFIGURED);
    }

    @Override
    public void deleteObjectsWithPrefix(String prefix) {
        throw new IllegalStateException(STORAGE_NOT_CONFIGURED);
    }

    @Override
    public void deleteObject(String objectName) {
        throw new IllegalStateException(STORAGE_NOT_CONFIGURED);
    }
}
