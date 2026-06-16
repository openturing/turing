package com.viglet.turing.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.viglet.core.storage.VigletStorageService;
import com.viglet.turing.api.asset.TurAssetItem;

/**
 * Adapts a neutral {@code viglet-core} {@link VigletStorageService} backend to
 * Turing's {@link TurStorageService} API (Block Q / T368).
 *
 * <p>It forwards every call to the lifted backend and maps the neutral
 * core types back to Turing's {@link TurAssetItem} / {@link TurStorageObjectStat}
 * / {@link TurStorageType}. The web-only {@link #uploadObject(MultipartFile, String)}
 * convenience lives here (the core SPI is stream-based and carries no Spring-web
 * dependency).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
public abstract class TurStorageServiceAdapter implements TurStorageService {

    private VigletStorageService delegate;

    protected TurStorageServiceAdapter(VigletStorageService delegate) {
        this.delegate = delegate;
    }

    /**
     * Replace the backend (e.g. rebuilt from freshly-read configuration inside
     * {@code init()}, mirroring the pre-extraction lazy-config behaviour).
     */
    protected void setDelegate(VigletStorageService delegate) {
        this.delegate = delegate;
    }

    /** Invoke the backend's initialization hook (call from a {@code @PostConstruct}). */
    protected void initBackend() {
        delegate.init();
    }

    @Override
    public TurStorageType getType() {
        return TurStorageMapper.toType(delegate.getType());
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled();
    }

    @Override
    public List<TurAssetItem> listObjects(String prefix) {
        return TurStorageMapper.toAssetItems(delegate.listObjects(prefix));
    }

    @Override
    public List<TurAssetItem> listAllObjects() {
        return TurStorageMapper.toAssetItems(delegate.listAllObjects());
    }

    @Override
    public InputStream downloadObject(String objectName) {
        return delegate.downloadObject(objectName);
    }

    @Override
    public TurStorageObjectStat statObject(String objectName) {
        return TurStorageMapper.toStat(delegate.statObject(objectName));
    }

    @Override
    public void uploadObject(MultipartFile file, String prefix) {
        String objectName = (prefix != null && !prefix.isBlank() ? prefix : "") + file.getOriginalFilename();
        try (InputStream is = file.getInputStream()) {
            String contentType = file.getContentType() != null
                    ? file.getContentType()
                    : TurStorageContentTypes.DEFAULT_CONTENT_TYPE;
            delegate.uploadStream(objectName, is, file.getSize(), contentType);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to upload object: " + objectName, e);
        }
    }

    @Override
    public void createFolder(String folderPath) {
        delegate.createFolder(folderPath);
    }

    @Override
    public void uploadStream(String objectName, InputStream inputStream, long size, String contentType) {
        delegate.uploadStream(objectName, inputStream, size, contentType);
    }

    @Override
    public void deleteObjectsWithPrefix(String prefix) {
        delegate.deleteObjectsWithPrefix(prefix);
    }

    @Override
    public void deleteObject(String objectName) {
        delegate.deleteObject(objectName);
    }
}
