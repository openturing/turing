package com.viglet.turing.service.storage;

/**
 * Storage-agnostic object metadata.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public record TurStorageObjectStat(
        String objectName,
        long size,
        String contentType,
        String lastModified
) {}
