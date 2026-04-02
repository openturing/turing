package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * MinIO connection properties bound to {@code turing.storage.minio.*}.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Getter
@Setter
public class TurMinioProperty {
	private String endpoint;
	private String accessKey;
	private String secretKey;
	private String bucket;
}
