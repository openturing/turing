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

import com.viglet.turing.commons.utils.TurCommonsUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

/**
 * @author Alexandre Oliveira
 * @since 0.3.5
 */
@Slf4j
@Component
public class TurSolrCache {
    public static final String SELECT = "/select";

    public boolean isSolrCoreExists(String urlString) {

        try {
            return isValidResponse(URI.create(urlString.concat(SELECT)).toURL());
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }

    private static boolean isValidResponse(URL url) {
        try {
        return TurCommonsUtils.isValidUrl(url) &&
                ((HttpURLConnection) url.openConnection()).getResponseCode() == 200;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }
}
