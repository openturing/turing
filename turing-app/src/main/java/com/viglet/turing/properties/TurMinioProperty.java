package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * MinIO configuration properties bound to {@code turing.minio.*}.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
 */
@Getter
@Setter
public class TurMinioProperty {
	private boolean enabled;
	private String endpoint;
	private String accessKey;
	private String secretKey;
	private String bucket;
}
