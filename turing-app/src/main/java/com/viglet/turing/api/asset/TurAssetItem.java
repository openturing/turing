package com.viglet.turing.api.asset;

/**
 * DTO representing a single object stored in MinIO.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
public record TurAssetItem(
		String name,
		long size,
		String contentType,
		String lastModified,
		boolean directory
) {
}
