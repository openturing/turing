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
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpJdkSolrClient;
import org.apache.solr.client.solrj.request.UpdateRequest;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.NamedList;
import org.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.sn.field.TurSNSiteFieldService;

/**
 * Unit tests for TurSolrDocumentHandler.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.10
 */
@ExtendWith(MockitoExtension.class)
class TurSolrDocumentHandlerTest {

        private static final String LINE_SEPARATOR = System.lineSeparator();

        @Mock
        private TurSNSiteFieldService turSNSiteFieldService;

        @Mock
        private HttpJdkSolrClient httpJdkSolrClient;

        @Mock
        private TurSNSite turSNSite;

        @Mock
        private TurDecimalFieldNormalizer turDecimalFieldNormalizer;

        private TurSolrDocumentHandler turSolrDocumentHandler;
        private CapturingSolrClient capturingSolrClient;
        private TurSolrInstance turSolrInstance;

        @BeforeEach
        void setUp() throws MalformedURLException {
                turSolrDocumentHandler = new TurSolrDocumentHandler(1000, turSNSiteFieldService,
                                turDecimalFieldNormalizer);
                capturingSolrClient = new CapturingSolrClient();
                turSolrInstance = new TurSolrInstance(httpJdkSolrClient,
                                URI.create("http://localhost:8983/solr").toURL(),
                                "core");
                turSolrInstance.setSolrClient(capturingSolrClient);
        }

        @Test
        void testIndexingBuildsDocumentWithExpectedFields() {
                TurSNSiteField multiValuedField = TurSNSiteField.builder()
                                .name("tags")
                                .multiValued(1)
                                .build();
                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("tags", multiValuedField);
                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("title", new JSONArray(List.of("first", "second")));
                attributes.put("tags", new JSONArray(List.of("alpha", "beta")));
                attributes.put("turing_entity_person", new ArrayList<>(List.of("Alice", "Bob")));
                attributes.put("count", 42);
                attributes.put(TurSolrConstants.SCORE, 1.0);
                attributes.put(TurSolrConstants.VERSION, 3);
                attributes.put(TurSolrConstants.BOOST, 2.5);

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                List<SolrInputDocument> documents = updateRequest.getDocuments();
                assertThat(documents).hasSize(1);

                SolrInputDocument document = documents.getFirst();
                assertThat(document.getFieldValue("title"))
                                .isEqualTo("first" + LINE_SEPARATOR + "second");
                assertThat(document.getFieldValues("tags"))
                                .containsExactly("alpha", "beta");
                assertThat(document.getFieldValues("turing_entity_person"))
                                .containsExactly("Alice", "Bob");
                assertThat(document.getFieldValue("count")).isEqualTo(42);
                assertThat(document.getFieldValue(TurSolrConstants.SCORE)).isNull();
                assertThat(document.getFieldValue(TurSolrConstants.VERSION)).isNull();
                assertThat(document.getFieldValue(TurSolrConstants.BOOST)).isNull();
        }

        @Test
        void testIndexingFormatsCurrencyAsAmountAndIsoCode() {
                TurSNSiteField currencyField = TurSNSiteField.builder()
                                .name("preco_produto")
                                .type(TurSEFieldType.CURRENCY)
                                .multiValued(0)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("preco_produto", currencyField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.CURRENCY)).thenReturn(true);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("preco_produto", "150.00,BRL");

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();

                assertThat(document.getFieldValue("preco_produto")).isEqualTo("150.00,BRL");
        }

        private static final class CapturingSolrClient extends SolrClient {
                private SolrRequest<?> lastRequest;

                @Override
                public NamedList<Object> request(SolrRequest<?> request, String collection)
                                throws SolrServerException, IOException {
                        this.lastRequest = request;
                        return new NamedList<>();
                }

                @Override
                public void close() {
                        // No resources to close for this test stub.
                }

                public SolrRequest<?> getLastRequest() {
                        return lastRequest;
                }
        }
}
