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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 *
 */
@Component
public class TurSolrCache {
    private static final Logger logger = LogManager.getLogger(MethodHandles.lookup().lookupClass());

    @Cacheable(value = "solrCore", sync = true)
    public boolean isSolrCoreExists(String urlString) {
        int responseCode = 0;
        URL url;
        try {
            url = new URL(urlString.concat("/select"));
            HttpURLConnection huc = (HttpURLConnection) url.openConnection();
            responseCode = huc.getResponseCode();
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
        return responseCode == 200;
    }
}
