package com.viglet.turing.service.storage;

import com.viglet.core.storage.VigletStorageContentTypes;

/**
 * Shared utility for guessing MIME content types from file extensions.
 *
 * <p>Now a thin alias over {@link VigletStorageContentTypes} (Block Q / T368):
 * the canonical extension→MIME map lives in {@code viglet-core-storage}. Kept as
 * a Turing-local entry point so existing callers (assets, pages, workspace, git)
 * need no change.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
public final class TurStorageContentTypes {

    public static final String DEFAULT_CONTENT_TYPE = VigletStorageContentTypes.DEFAULT_CONTENT_TYPE;

    private TurStorageContentTypes() {
    }

    public static String guessContentType(String name) {
        return VigletStorageContentTypes.guessContentType(name);
    }
}
