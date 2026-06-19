/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 */
package com.viglet.turing.solr.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.viglet.turing.persistence.model.se.TurSEInstance;

/**
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
class TurSolrPropertySourceTest {

    private static final String ENDPOINT = "http://solr.example.org:8983/solr";

    @Test
    void reportsReadOnlyAndExposesEndpoint() {
        TurSolrPropertySource source = new TurSolrPropertySource(ENDPOINT);

        assertThat(source.isReadOnly()).isTrue();
        assertThat(source.getConfiguredEndpoint()).isEqualTo(ENDPOINT);
    }

    @Test
    void findAllReturnsSyntheticInstance() {
        TurSolrPropertySource source = new TurSolrPropertySource(ENDPOINT);

        assertThat(source.findAll()).hasSize(1)
                .first()
                .extracting(TurSEInstance::getEndpointUrl)
                .isEqualTo(ENDPOINT);
    }

    @Test
    void findByIdReturnsSyntheticInstanceForKnownId() {
        TurSolrPropertySource source = new TurSolrPropertySource(ENDPOINT);

        assertThat(source.findById(TurSolrPropertySource.PROPERTY_INSTANCE_ID)).isPresent();
        assertThat(source.findById("something-else")).isEmpty();
    }

    @Test
    void saveIsNotSupported() {
        TurSolrPropertySource source = new TurSolrPropertySource(ENDPOINT);

        TurSEInstance instance = new TurSEInstance();
        assertThatThrownBy(() -> source.save(instance))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void deleteIsNotSupported() {
        TurSolrPropertySource source = new TurSolrPropertySource(ENDPOINT);

        assertThatThrownBy(() -> source.delete("any"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
