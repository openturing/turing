/*
 * Copyright (C) 2016-2022 the original author or authors.
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
package com.viglet.turing.plugins.se.elasticsearch;

import com.viglet.turing.elasticsearch.TurElasticsearch;
import com.viglet.turing.elasticsearch.TurElasticsearchInstanceProcess;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.plugins.se.TurSearchEnginePlugin;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Elasticsearch implementation of the search engine plugin interface.
 *
 * @author Alexandre Oliveira
 * @since 2025.4.4
 */
@Slf4j
@Component
public class TurElasticsearchSearchEnginePlugin implements TurSearchEnginePlugin {

    private final TurElasticsearch turElasticsearch;
    private final TurElasticsearchInstanceProcess turElasticsearchInstanceProcess;

    public TurElasticsearchSearchEnginePlugin(
            TurElasticsearch turElasticsearch,
            TurElasticsearchInstanceProcess turElasticsearchInstanceProcess) {
        this.turElasticsearch = turElasticsearch;
        this.turElasticsearchInstanceProcess = turElasticsearchInstanceProcess;
    }

    @Override
    public Optional<TurSEResults> retrieveSearchResults(TurSNSiteSearchContext context) {
        return turElasticsearchInstanceProcess
                .initElasticsearchInstance(context.getSiteName(), context.getLocale())
                .flatMap(elasticsearchInstance -> turElasticsearch.retrieveElasticsearchFromSN(elasticsearchInstance, context));
    }

    @Override
    public Optional<TurSEResults> retrieveFacetResults(TurSNSiteSearchContext context, String facetName) {
        return turElasticsearchInstanceProcess
                .initElasticsearchInstance(context.getSiteName(), context.getLocale())
                .flatMap(elasticsearchInstance -> turElasticsearch.retrieveFacetElasticsearchFromSN(
                        elasticsearchInstance, context, facetName));
    }

    @Override
    public String getPluginType() {
        return "elasticsearch";
    }
}

