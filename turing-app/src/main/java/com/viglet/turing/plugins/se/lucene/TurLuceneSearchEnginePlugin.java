/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.viglet.turing.plugins.se.lucene;

import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.lucene.TurLucene;
import com.viglet.turing.lucene.TurLuceneInstanceProcess;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import com.viglet.turing.se.result.TurSEResults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Lucene implementation of {@link TurSearchEnginePlugin}.
 *
 * <p>This plugin stores indexes on the local filesystem under the path
 * configured in {@code TurSEInstance.endpointUrl}. Each locale maps to a
 * sub-directory (the "core") inside that base path, matching the
 * {@code TurSNSiteLocale.core} field.
 *
 * <p>To activate this plugin, set the SE vendor plugin type to {@code "lucene"}
 * and point the SE instance endpoint URL to a writable directory, e.g.
 * {@code /data/turing/lucene}.
 *
 * @author Alexandre Oliveira
 * @since 2026.1
 */
@Slf4j
@Component
public class TurLuceneSearchEnginePlugin implements TurSearchEnginePlugin {

    private final TurLucene turLucene;
    private final TurLuceneInstanceProcess turLuceneInstanceProcess;

    public TurLuceneSearchEnginePlugin(TurLucene turLucene,
            TurLuceneInstanceProcess turLuceneInstanceProcess) {
        this.turLucene = turLucene;
        this.turLuceneInstanceProcess = turLuceneInstanceProcess;
    }

    @Override
    public Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context) {
        return turLuceneInstanceProcess
                .initLuceneInstance(context.getSiteName(), context.getLocale())
                .flatMap(instance -> turLucene.retrieveLuceneFromSN(instance, context));
    }

    @Override
    public Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context,
            String facetName) {
        return turLuceneInstanceProcess
                .initLuceneInstance(context.getSiteName(), context.getLocale())
                .flatMap(instance -> turLucene.retrieveFacetLuceneFromSN(instance, context, facetName));
    }

    @Override
    public String getPluginType() {
        return "lucene";
    }
}
