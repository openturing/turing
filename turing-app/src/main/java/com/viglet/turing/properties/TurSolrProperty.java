package com.viglet.turing.properties;

import lombok.Getter;
import lombok.Setter;

/**
 * Solr configuration properties.
 *
 * When {@code enabled} is {@code true} and {@code endpoint} is provided, the
 * runtime ignores JPA-stored {@code TurSEInstance} entries for Solr and uses
 * the configured endpoint instead. In this mode the SE instance CRUD is
 * read-only from the UI/API perspective.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Getter
@Setter
public class TurSolrProperty {
	private int timeout;
	private boolean cloud;
	private boolean enabled;
	private String endpoint;
}
