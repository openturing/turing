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
 * Tests for TurSolrDocumentHandler.
 *
 * @author Alexandre Oliveira
 * @since 2026.1.14
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

        @Test
        void testIndexingRemovesScoreVersionAndBoostKeys() {
                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("id", "1");
                attributes.put(TurSolrConstants.SCORE, 5.0);
                attributes.put(TurSolrConstants.VERSION, 99);
                attributes.put(TurSolrConstants.BOOST, 1.5);

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue(TurSolrConstants.SCORE)).isNull();
                assertThat(document.getFieldValue(TurSolrConstants.VERSION)).isNull();
                assertThat(document.getFieldValue(TurSolrConstants.BOOST)).isNull();
                assertThat(document.getFieldValue("id")).isEqualTo("1");
        }

        @Test
        void testIndexingHandlesNullAttributesThrowsNpe() {
                // The indexing method calls attributes.remove() before null-checking,
                // so null attributes cause a NullPointerException
                assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                                NullPointerException.class,
                                () -> turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, null)))
                                .isNotNull();
        }

        @Test
        void testIndexingHandlesEmptyAttributes() {
                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(new HashMap<>());

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, new HashMap<>());

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldNames()).isEmpty();
        }

        @Test
        void testIndexingSingleValuedJsonArrayConcatenatesValues() {
                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("desc", TurSNSiteField.builder().name("desc").multiValued(0).build());
                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("desc", new org.json.JSONArray(List.of("line1", "line2")));

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                String value = (String) document.getFieldValue("desc");
                assertThat(value).contains("line1");
                assertThat(value).contains("line2");
        }

        @Test
        void testIndexingSingleValuedArrayListConcatenatesValues() {
                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("body", TurSNSiteField.builder().name("body").multiValued(0).build());
                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("body", new ArrayList<>(List.of("para1", "para2")));

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                String value = (String) document.getFieldValue("body");
                assertThat(value).contains("para1");
                assertThat(value).contains("para2");
        }

        @Test
        void testIndexingMultiValuedArrayListAddsEachValue() {
                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("authors", TurSNSiteField.builder().name("authors").multiValued(1).build());
                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("authors", new ArrayList<>(List.of("Alice", "Bob")));

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValues("authors")).containsExactly("Alice", "Bob");
        }

        @Test
        void testIndexingTuringEntityPrefixAlwaysMultiValued() {
                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("turing_entity_org", new org.json.JSONArray(List.of("Viglet", "Turing")));

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValues("turing_entity_org")).containsExactly("Viglet", "Turing");
        }

        @Test
        void testIndexingIntegerFieldPreservesType() {
                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("count", 42);

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("count")).isEqualTo(42);
        }

        @Test
        void testIndexingNullAttributeValueIsSkipped() {
                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("id", "1");
                attributes.put("nullField", null);

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("id")).isEqualTo("1");
                assertThat(document.getFieldValue("nullField")).isNull();
        }

        @Test
        void testCurrencyInvalidFormatIsSkipped() {
                TurSNSiteField currencyField = TurSNSiteField.builder()
                                .name("price")
                                .type(TurSEFieldType.CURRENCY)
                                .multiValued(0)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("price", currencyField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.CURRENCY)).thenReturn(true);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("price", "invalid");

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("price")).isNull();
        }

        @Test
        void testCurrencyWithCommaSeparatedISOCodeIsValid() {
                TurSNSiteField currencyField = TurSNSiteField.builder()
                                .name("amount")
                                .type(TurSEFieldType.CURRENCY)
                                .multiValued(0)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("amount", currencyField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.CURRENCY)).thenReturn(true);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("amount", "99.50,USD");

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("amount")).isEqualTo("99.50,USD");
        }

        @Test
        void testCurrencyLowercaseISOCodeIsNormalized() {
                TurSNSiteField currencyField = TurSNSiteField.builder()
                                .name("amount")
                                .type(TurSEFieldType.CURRENCY)
                                .multiValued(0)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("amount", currencyField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.CURRENCY)).thenReturn(true);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("amount", "100.00,brl");

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("amount")).isEqualTo("100.00,BRL");
        }

        @Test
        void testCurrencyMissingCodeIsInvalid() {
                TurSNSiteField currencyField = TurSNSiteField.builder()
                                .name("price")
                                .type(TurSEFieldType.CURRENCY)
                                .multiValued(0)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("price", currencyField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.CURRENCY)).thenReturn(true);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("price", "100.00,");

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("price")).isNull();
        }

        @Test
        void testDeIndexingCallsDeleteById() {
                turSolrDocumentHandler.deIndexing(turSolrInstance, "doc-123");

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                assertThat(updateRequest).isNotNull();
                assertThat(updateRequest.getDeleteById()).contains("doc-123");
        }

        @Test
        void testDeIndexingByTypeCallsDeleteByQuery() {
                turSolrDocumentHandler.deIndexingByType(turSolrInstance, "article");

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                assertThat(updateRequest).isNotNull();
                // The deleteByQuery sets query "type:article"
                assertThat(updateRequest.getDeleteQuery()).contains("type:article");
        }

        @Test
        void testCurrencyNullValueIsSkipped() {
                TurSNSiteField currencyField = TurSNSiteField.builder()
                                .name("price")
                                .type(TurSEFieldType.CURRENCY)
                                .multiValued(0)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("price", currencyField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("price", null);

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("price")).isNull();
        }

        @Test
        void testCurrencyMultiValuedJsonArray() {
                TurSNSiteField currencyField = TurSNSiteField.builder()
                                .name("prices")
                                .type(TurSEFieldType.CURRENCY)
                                .multiValued(1)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("prices", currencyField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.CURRENCY)).thenReturn(true);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("prices", new org.json.JSONArray(List.of("100.00,USD", "200.00,EUR")));

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValues("prices")).containsExactly("100.00,USD", "200.00,EUR");
        }

        @Test
        void testCurrencySingleValuedJsonArrayUsesFirst() {
                TurSNSiteField currencyField = TurSNSiteField.builder()
                                .name("price")
                                .type(TurSEFieldType.CURRENCY)
                                .multiValued(0)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("price", currencyField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.CURRENCY)).thenReturn(true);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("price", new org.json.JSONArray(List.of("50.00,GBP", "75.00,EUR")));

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("price")).isEqualTo("50.00,GBP");
        }

        @Test
        void testCurrencyEmptyJsonArrayIsSkipped() {
                TurSNSiteField currencyField = TurSNSiteField.builder()
                                .name("price")
                                .type(TurSEFieldType.CURRENCY)
                                .multiValued(0)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("price", currencyField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.CURRENCY)).thenReturn(true);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("price", new org.json.JSONArray());

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("price")).isNull();
        }

        @Test
        void testCurrencyMultiValuedArrayList() {
                TurSNSiteField currencyField = TurSNSiteField.builder()
                                .name("prices")
                                .type(TurSEFieldType.CURRENCY)
                                .multiValued(1)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("prices", currencyField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.CURRENCY)).thenReturn(true);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("prices", new ArrayList<>(List.of("100.00,USD", "200.00,EUR")));

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValues("prices")).containsExactly("100.00,USD", "200.00,EUR");
        }

        @Test
        void testCurrencySingleValuedArrayListUsesFirst() {
                TurSNSiteField currencyField = TurSNSiteField.builder()
                                .name("price")
                                .type(TurSEFieldType.CURRENCY)
                                .multiValued(0)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("price", currencyField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.CURRENCY)).thenReturn(true);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("price", new ArrayList<>(List.of("50.00,GBP", "75.00,EUR")));

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("price")).isEqualTo("50.00,GBP");
        }

        @Test
        void testCurrencyEmptyArrayListIsSkipped() {
                TurSNSiteField currencyField = TurSNSiteField.builder()
                                .name("price")
                                .type(TurSEFieldType.CURRENCY)
                                .multiValued(0)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("price", currencyField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.CURRENCY)).thenReturn(true);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("price", new ArrayList<>());

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("price")).isNull();
        }

        @Test
        void testDecimalFieldNonCurrencyUsesNormalizer() {
                TurSNSiteField floatField = TurSNSiteField.builder()
                                .name("weight")
                                .type(TurSEFieldType.FLOAT)
                                .multiValued(0)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("weight", floatField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.FLOAT)).thenReturn(true);
                when(turDecimalFieldNormalizer.normalizeNumericValue(
                                org.mockito.ArgumentMatchers.eq(TurSEFieldType.FLOAT), org.mockito.ArgumentMatchers.any()))
                                .thenReturn(java.util.Optional.of(3.14));

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("weight", "3,14");

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("weight")).isEqualTo(3.14);
        }

        @Test
        void testDecimalFieldInvalidValueIsSkipped() {
                TurSNSiteField doubleField = TurSNSiteField.builder()
                                .name("weight")
                                .type(TurSEFieldType.DOUBLE)
                                .multiValued(0)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("weight", doubleField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.DOUBLE)).thenReturn(true);
                when(turDecimalFieldNormalizer.normalizeNumericValue(
                                org.mockito.ArgumentMatchers.eq(TurSEFieldType.DOUBLE), org.mockito.ArgumentMatchers.any()))
                                .thenReturn(java.util.Optional.empty());

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("weight", "not-a-number");

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("weight")).isNull();
        }

        @Test
        void testCurrencyWithCommaDecimalIsNormalized() {
                TurSNSiteField currencyField = TurSNSiteField.builder()
                                .name("price")
                                .type(TurSEFieldType.CURRENCY)
                                .multiValued(0)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("price", currencyField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.CURRENCY)).thenReturn(true);

                // "19,92,BRL" - comma decimal in amount part should be normalized to dot
                Map<String, Object> attributes = new HashMap<>();
                attributes.put("price", "19,92,BRL");

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("price")).isEqualTo("19.92,BRL");
        }

        @Test
        void testCurrencyInvalidCodeLengthIsInvalid() {
                TurSNSiteField currencyField = TurSNSiteField.builder()
                                .name("price")
                                .type(TurSEFieldType.CURRENCY)
                                .multiValued(0)
                                .build();

                Map<String, TurSNSiteField> fieldMap = new HashMap<>();
                fieldMap.put("price", currencyField);

                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(fieldMap);
                when(turDecimalFieldNormalizer.isDecimalFieldType(TurSEFieldType.CURRENCY)).thenReturn(true);

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("price", "100.00,ABCD");

                turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);

                UpdateRequest updateRequest = (UpdateRequest) capturingSolrClient.getLastRequest();
                SolrInputDocument document = updateRequest.getDocuments().getFirst();
                assertThat(document.getFieldValue("price")).isNull();
        }

        @Test
        void testIndexingPropagatesIoFailureInsteadOfSwallowing() {
                // Regression guard: a swallowed Solr/IO error used to falsely report the
                // document as "Indexed" while it was actually lost. It must now surface so
                // the resilience layer / circuit breaker can react.
                when(turSNSiteFieldService.toMap(turSNSite)).thenReturn(new HashMap<>());
                turSolrInstance.setSolrClient(new ThrowingSolrClient());

                Map<String, Object> attributes = new HashMap<>();
                attributes.put("id", "doc-io-fail");

                org.junit.jupiter.api.Assertions.assertThrows(
                                TurSolrDocumentHandler.TurSolrIndexException.class,
                                () -> turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes));
        }

        @Test
        void testDeIndexingPropagatesIoFailureInsteadOfSwallowing() {
                turSolrInstance.setSolrClient(new ThrowingSolrClient());

                org.junit.jupiter.api.Assertions.assertThrows(
                                TurSolrDocumentHandler.TurSolrIndexException.class,
                                () -> turSolrDocumentHandler.deIndexing(turSolrInstance, "doc-io-fail"));
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

        /** Solr client stub that simulates an IO failure on every request. */
        private static final class ThrowingSolrClient extends SolrClient {
                @Override
                public NamedList<Object> request(SolrRequest<?> request, String collection)
                                throws SolrServerException, IOException {
                        throw new IOException("disk full");
                }

                @Override
                public void close() {
                        // No resources to close for this test stub.
                }
        }
}
