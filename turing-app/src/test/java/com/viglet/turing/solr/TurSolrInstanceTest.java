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
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.mock;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for TurSolrInstance.
 *
 * @author Alexandre Oliveira
 * @since 2025.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSolrInstanceTest {

    @Mock
    private HttpJdkSolrClient httpJdkSolrClient;

    private URL solrUrl;
    private String core;

    @BeforeEach
    void setUp() throws MalformedURLException {
        solrUrl = URI.create("http://localhost:8983/solr").toURL();
        core = "testCore";
    }

    @Test
    void testConstructor() {
        TurSolrInstance instance = new TurSolrInstance(httpJdkSolrClient, solrUrl, core);

        assertThat(instance.getSolrUrl()).isEqualTo(solrUrl);
        assertThat(instance.getCore()).isEqualTo(core);
        assertThat(instance.getSolrClient()).isNotNull();
    }

    @Test
    void testConstructorCreatesNewSolrClient() {
        TurSolrInstance instance = new TurSolrInstance(httpJdkSolrClient, solrUrl, core);

        assertThat(instance.getSolrClient()).isInstanceOf(SolrClient.class);
    }

    @Test
    void testGettersAndSetters() {
        TurSolrInstance instance = new TurSolrInstance(httpJdkSolrClient, solrUrl, core);

        assertThat(instance.getSolrUrl()).isEqualTo(solrUrl);
        assertThat(instance.getCore()).isEqualTo(core);

        SolrClient newSolrClient = mock(SolrClient.class);
        URL newUrl = null;
        try {
            newUrl = URI.create("http://localhost:9000/solr").toURL();
        } catch (MalformedURLException e) {
            fail("Failed to create URL");
        }
        String newCore = "newCore";

        instance.setSolrClient(newSolrClient);
        instance.setSolrUrl(newUrl);
        instance.setCore(newCore);

        assertThat(instance.getSolrClient()).isEqualTo(newSolrClient);
        assertThat(instance.getSolrUrl()).isEqualTo(newUrl);
        assertThat(instance.getCore()).isEqualTo(newCore);
    }

    @Test
    void testConstructorWithDifferentCoreName() {
        String customCore = "customCore";
        TurSolrInstance instance = new TurSolrInstance(httpJdkSolrClient, solrUrl, customCore);

        assertThat(instance.getCore()).isEqualTo(customCore);
    }

    @Test
    void testConstructorWithDifferentUrl() throws MalformedURLException {
        URL customUrl = URI.create("http://custom-solr:8080/solr").toURL();
        TurSolrInstance instance = new TurSolrInstance(httpJdkSolrClient, customUrl, core);

        assertThat(instance.getSolrUrl()).isEqualTo(customUrl);
        assertThat(instance.getSolrClient()).isNotNull();
    }

    @Test
    void testCoreFieldCanBeNull() {
        TurSolrInstance instance = new TurSolrInstance(httpJdkSolrClient, solrUrl, null);

        assertThat(instance.getCore()).isNull();
    }

    @Test
    void testSetCoreAfterConstruction() {
        TurSolrInstance instance = new TurSolrInstance(httpJdkSolrClient, solrUrl, core);
        String newCore = "anotherCore";

        instance.setCore(newCore);

        assertThat(instance.getCore()).isEqualTo(newCore);
    }
}
