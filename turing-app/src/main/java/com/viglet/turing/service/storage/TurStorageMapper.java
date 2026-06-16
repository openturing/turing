package com.viglet.turing.service.storage;

import java.util.List;

import com.viglet.core.storage.VigletStorageObject;
import com.viglet.core.storage.VigletStorageObjectStat;
import com.viglet.core.storage.VigletStorageProperties;
import com.viglet.core.storage.VigletStorageType;
import com.viglet.turing.api.asset.TurAssetItem;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurMinioProperty;
import com.viglet.turing.properties.TurStorageProperty;

/**
 * Maps between Turing's storage API types and the {@code viglet-core} storage
 * types (Block Q / T368).
 *
 * <p>The backend logic was lifted into {@code viglet-core-storage}; the Turing
 * layer keeps its own {@link TurAssetItem} / {@link TurStorageType} /
 * {@link TurStorageObjectStat} API shapes (which controllers and the frontend
 * depend on) and adapts to the neutral core types at the seam.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
final class TurStorageMapper {

    private TurStorageMapper() {
    }

    static TurStorageType toType(VigletStorageType type) {
        return switch (type) {
            case NONE -> TurStorageType.NONE;
            case MINIO -> TurStorageType.MINIO;
            case FILESYSTEM -> TurStorageType.FILESYSTEM;
        };
    }

    static TurAssetItem toAssetItem(VigletStorageObject object) {
        return new TurAssetItem(object.name(), object.size(), object.contentType(),
                object.lastModified(), object.directory());
    }

    static List<TurAssetItem> toAssetItems(List<VigletStorageObject> objects) {
        return objects.stream().map(TurStorageMapper::toAssetItem).toList();
    }

    static TurStorageObjectStat toStat(VigletStorageObjectStat stat) {
        return new TurStorageObjectStat(stat.objectName(), stat.size(), stat.contentType(), stat.lastModified());
    }

    /**
     * Projects Turing's {@code turing.storage.*} configuration onto the neutral
     * {@link VigletStorageProperties} consumed by the core backends.
     */
    static VigletStorageProperties toCoreProperties(TurConfigProperties configProperties) {
        VigletStorageProperties core = new VigletStorageProperties();
        TurStorageProperty storage = configProperties != null ? configProperties.getStorage() : null;
        if (storage == null) {
            return core;
        }
        if (storage.getType() != null) {
            core.setType(toCoreType(storage.getType()));
        }
        if (storage.getFilesystem() != null && storage.getFilesystem().getPath() != null) {
            core.getFilesystem().setPath(storage.getFilesystem().getPath());
        }
        TurMinioProperty minio = storage.getMinio();
        if (minio != null) {
            core.getMinio().setEndpoint(minio.getEndpoint());
            core.getMinio().setAccessKey(minio.getAccessKey());
            core.getMinio().setSecretKey(minio.getSecretKey());
            core.getMinio().setBucket(minio.getBucket());
        }
        return core;
    }

    private static VigletStorageType toCoreType(TurStorageType type) {
        return switch (type) {
            case NONE -> VigletStorageType.NONE;
            case MINIO -> VigletStorageType.MINIO;
            case FILESYSTEM -> VigletStorageType.FILESYSTEM;
        };
    }
}
