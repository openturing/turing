package com.viglet.turing.service.asset;

/**
 * Event published when an asset is uploaded or deleted in MinIO.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
public record TurAssetEvent(Type type, String objectName, String contentType, long size) {

    public enum Type { UPLOADED, DELETED }

    public static TurAssetEvent uploaded(String objectName, String contentType, long size) {
        return new TurAssetEvent(Type.UPLOADED, objectName, contentType, size);
    }

    public static TurAssetEvent deleted(String objectName) {
        return new TurAssetEvent(Type.DELETED, objectName, "", 0);
    }
}
