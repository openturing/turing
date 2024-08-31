/*
 * Copyright (C) 2016-2022 the original author or authors.
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
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.viglet.turing.solr;

import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.solr.client.solrj.SolrClient;

import java.io.IOException;
import java.net.URL;

@Slf4j
@Getter
@Setter
public class TurSolrInstance {
    private CloseableHttpClient closeableHttpClient;
    private SolrClient solrClient;
    private String core;
    private URL solrUrl;

    @PreDestroy
    public void destroy() {
        if (log.isDebugEnabled()) {
            log.debug("TurSolrInstance destroyed");
        }
        this.close();
    }

    public void close() {
        try {
            if (solrClient != null) {
                solrClient.close();
                solrClient = null;
            }
            if (closeableHttpClient != null) {
                closeableHttpClient.close();
                closeableHttpClient = null;
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public TurSolrInstance(CloseableHttpClient closeableHttpClient, SolrClient solrClient, URL solrUrl,
                           String core) {
        super();
        this.closeableHttpClient = closeableHttpClient;
        this.solrClient = solrClient;
        this.solrUrl = solrUrl;
        this.core = core;
    }
}
