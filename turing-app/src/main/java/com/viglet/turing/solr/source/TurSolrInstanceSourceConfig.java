/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 */
package com.viglet.turing.solr.source;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.properties.TurConfigProperties;
import com.viglet.turing.properties.TurSolrProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Selects the active {@link TurSolrInstanceSource} implementation at startup.
 *
 * <p>When {@code turing.solr.enabled=true} <em>and</em>
 * {@code turing.solr.endpoint} is non-blank, the property-backed
 * implementation is wired. Otherwise the JPA-backed implementation is used.
 *
 * @author Alexandre Oliveira
 * @since 2026.2.4
 */
@Slf4j
@Configuration
public class TurSolrInstanceSourceConfig {

    @Bean
    public TurSolrInstanceSource turSolrInstanceSource(TurConfigProperties configProperties,
                                                       TurSEInstanceRepository repository) {
        TurSolrProperty solr = configProperties.getSolr();
        if (solr != null && solr.isEnabled()
                && solr.getEndpoint() != null && !solr.getEndpoint().isBlank()) {
            log.info("Solr source: PROPERTY (turing.solr.endpoint={})", solr.getEndpoint());
            return new TurSolrPropertySource(solr.getEndpoint().trim());
        }
        log.info("Solr source: JPA");
        return new TurSolrJpaSource(repository);
    }
}
