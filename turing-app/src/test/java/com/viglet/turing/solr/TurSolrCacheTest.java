/*
 * Copyright (C) 2016-2025 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.utils.TurCommonsUtils;

/**
 * Unit tests for TurSolrCache.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSolrCacheTest {

    @Test
    void testIsValidResponseReturnsTrueWhenResponseIsOk() throws Exception {
        URL url = mock(URL.class);
        HttpURLConnection connection = mock(HttpURLConnection.class);
        when(url.openConnection()).thenReturn(connection);
        when(connection.getResponseCode()).thenReturn(200);

        try (MockedStatic<TurCommonsUtils> utilities = mockStatic(TurCommonsUtils.class)) {
            utilities.when(() -> TurCommonsUtils.isValidUrl(url)).thenReturn(true);
            boolean result = invokeIsValidResponse(url);
            assertThat(result).isTrue();
        }
    }

    @Test
    void testIsValidResponseReturnsFalseWhenUrlIsInvalid() throws Exception {
        URL url = mock(URL.class);

        try (MockedStatic<TurCommonsUtils> utilities = mockStatic(TurCommonsUtils.class)) {
            utilities.when(() -> TurCommonsUtils.isValidUrl(url)).thenReturn(false);
            boolean result = invokeIsValidResponse(url);
            assertThat(result).isFalse();
        }
    }

    @Test
    void testIsValidResponseReturnsFalseOnIOException() throws Exception {
        URL url = mock(URL.class);
        when(url.openConnection()).thenThrow(new IOException("boom"));

        try (MockedStatic<TurCommonsUtils> utilities = mockStatic(TurCommonsUtils.class)) {
            utilities.when(() -> TurCommonsUtils.isValidUrl(url)).thenReturn(true);
            boolean result = invokeIsValidResponse(url);
            assertThat(result).isFalse();
        }
    }

    private static boolean invokeIsValidResponse(URL url) throws Exception {
        Method method = TurSolrCache.class.getDeclaredMethod("isValidResponse", URL.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, url);
    }
}
