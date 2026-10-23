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
package com.viglet.turing.plugins.se;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequestInterceptor;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.field.TurSNFieldName;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.elasticsearch.TurElasticsearch;
import com.viglet.turing.elasticsearch.TurElasticsearchInstance;
import com.viglet.turing.elasticsearch.TurElasticsearchUtils;
import com.viglet.turing.lucene.TurLuceneCustomFacets;
import com.viglet.turing.lucene.TurLuceneDocumentHandler;
import com.viglet.turing.lucene.TurLuceneInstance;
import com.viglet.turing.lucene.TurLuceneQueryBuilder;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSort;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortItem;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortMissingEnum;
import com.viglet.turing.persistence.model.sn.sort.TurSNSiteCustomSortOrderEnum;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.sort.TurSNSiteCustomSortRepository;
import com.viglet.turing.sn.field.TurSNSiteFieldService;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;

/**
 * T1107 / §CXX — one Semantic Navigation request, two engines, the same documents.
 *
 * <p>The same small job catalog is indexed into Lucene, a live Elasticsearch and a
 * live Solr (the latest, 10, user-managed), each through its own production path:
 * Lucene through {@link TurLuceneDocumentHandler}; Elasticsearch into an index
 * created by {@link TurElasticsearchUtils#createIndex} (the mapping under test) and
 * filled by {@link TurElasticsearch#indexing}; Solr into a core created from the
 * configset the Turing Solr image ships, its fields declared by the provisioning
 * call, filled and searched through a real {@code TurSolr}. Every row of the table
 * is then asked of all three engines and must return the same ids: facet filters
 * in each shape
 * ({@code fq}, {@code fqAnd}, {@code fqOr}, {@code fqItemOperator}, ranges, ISO
 * dates, a custom facet selection) and every kind of sort ({@code newest},
 * {@code field:dir}, custom sorts with missing placement first and last).
 *
 * <p>The free text stays {@code *}: how text is matched is each engine's own.
 * Building the Solr row found T1109 (no core loaded from any Turing configset),
 * T1110 (core creation refused by Solr 10) and T1111 (exclusive-start ranges
 * returning nothing).
 *
 * @author Alexandre Oliveira
 * @since 2026.3.5
 */
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TurSNEngineConformanceIT {

    private static final String INDEX = "turing_conformance_it";

    private static final GenericContainer<?> ES = new GenericContainer<>(DockerImageName
            .parse("docker.elastic.co/elasticsearch/elasticsearch:9.1.0"))
                    .withExposedPorts(9200)
                    .withEnv("discovery.type", "single-node")
                    .withEnv("xpack.security.enabled", "false")
                    .withEnv("ES_JAVA_OPTS", "-Xms512m -Xmx512m")
                    .waitingFor(Wait.forHttp("/_cluster/health")
                            .forStatusCode(200)
                            .withStartupTimeout(java.time.Duration.ofMinutes(4)));

    /** id, sector, salary, publishedAt, expiresAt (null = the job never closes). */
    private static final Object[][] JOBS = {
            { "creche", "educacao", 2000, "2026-10-20T00:00:00Z", "2026-11-30T00:00:00Z" },
            { "baba", "domestico", 1800, "2026-10-01T00:00:00Z", null },
            { "monitor", "educacao", 1500, "2026-09-01T00:00:00Z", "2026-10-30T00:00:00Z" },
            { "enfermeira", "saude", 4000, "2026-10-22T00:00:00Z", "2026-11-15T00:00:00Z" },
            { "dados", "tecnologia", 9000, "2026-08-01T00:00:00Z", null } };

    private static final Map<String, TurSEFieldType> FIELD_TYPES = new LinkedHashMap<>();

    static {
        FIELD_TYPES.put(TurSNFieldName.ID, TurSEFieldType.STRING);
        FIELD_TYPES.put("sector", TurSEFieldType.STRING);
        FIELD_TYPES.put("salary", TurSEFieldType.INT);
        FIELD_TYPES.put("publishedAt", TurSEFieldType.DATE);
        FIELD_TYPES.put("expiresAt", TurSEFieldType.DATE);
    }

    private static final String SOLR_CORE = "turing_conformance";

    private GenericContainer<?> solrContainer;
    private com.viglet.turing.solr.TurSolr solr;
    private com.viglet.turing.solr.TurSolrInstance solrInstance;
    private TurSNSite site;
    private TurElasticsearch elasticsearch;
    private TurElasticsearchInstance esInstance;
    private TurLuceneQueryBuilder luceneQueryBuilder;
    private IndexSearcher luceneSearcher;
    private DirectoryReader luceneReader;

    private static ElasticsearchClient client(String url) {
        URI uri = URI.create(url);
        RestClient restClient = RestClient
                .builder(new HttpHost(uri.getHost(), uri.getPort(), uri.getScheme()))
                .setHttpClientConfigCallback(builder -> {
                    builder.addInterceptorLast((HttpRequestInterceptor) (request, context) -> {
                        request.removeHeaders("Content-Type");
                        request.removeHeaders("Accept");
                        request.setHeader("Content-Type", "application/json");
                        request.setHeader("Accept", "application/json");
                    });
                    return builder;
                })
                .build();
        return new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    private static Map<String, Object> attributes(Object[] job) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put(TurSNFieldName.ID, job[0]);
        attrs.put("sector", job[1]);
        attrs.put("salary", job[2]);
        attrs.put("publishedAt", job[3]);
        if (job[4] != null) {
            attrs.put("expiresAt", job[4]);
        }
        return attrs;
    }

    private static TurSNSiteCustomSort customSort(String name, TurSNSiteCustomSortMissingEnum missing) {
        TurSNSiteCustomSort sort = TurSNSiteCustomSort.builder().name(name).build();
        sort.setItems(new HashSet<>(Set.of(TurSNSiteCustomSortItem.builder().fieldName("expiresAt")
                .sortOrder(TurSNSiteCustomSortOrderEnum.ASC).missing(missing).position(1).build())));
        return sort;
    }

    @BeforeAll
    void indexTheCatalogIntoBothEngines() throws Exception {
        site = new TurSNSite();
        site.setId("site-jobs");
        site.setName("jobs");
        site.setDefaultDateField("publishedAt");

        List<TurSNSiteFieldExt> fields = FIELD_TYPES.entrySet().stream().map(e -> {
            TurSNSiteFieldExt ext = new TurSNSiteFieldExt();
            ext.setId("field-" + e.getKey());
            ext.setName(e.getKey());
            ext.setType(e.getValue());
            return ext;
        }).toList();
        TurSNSiteFieldExtRepository fieldRepository = mock(TurSNSiteFieldExtRepository.class);
        when(fieldRepository.findByTurSNSiteAndEnabled(any(), anyInt())).thenReturn(fields);
        when(fieldRepository.findAll()).thenReturn(fields);
        when(fieldRepository.findByTurSNSiteAndFacetAndEnabledOrderByFacetPosition(any(), anyInt(), anyInt()))
                .thenReturn(List.of());
        TurSNSiteCustomSortRepository sortRepository = mock(TurSNSiteCustomSortRepository.class);
        when(sortRepository.findByTurSNSiteAndName(any(), anyString())).thenReturn(Optional.empty());
        when(sortRepository.findByTurSNSiteAndName(any(), eq("closingSoonest")))
                .thenReturn(Optional.of(customSort("closingSoonest", TurSNSiteCustomSortMissingEnum.LAST)));
        when(sortRepository.findByTurSNSiteAndName(any(), eq("openEndedFirst")))
                .thenReturn(Optional.of(customSort("openEndedFirst", TurSNSiteCustomSortMissingEnum.FIRST)));
        // A custom facet "posted" whose item "Recent" stands for publishedAt since Oct 15.
        TurLuceneCustomFacets customFacets = mock(TurLuceneCustomFacets.class);
        when(customFacets.resolveFilter(any(), anyString(), anyString())).thenReturn(Optional.empty());
        when(customFacets.resolveFilter(any(), eq("posted"), eq("Recent"))).thenReturn(Optional.of(
                new TurLuceneCustomFacets.ItemFilter("publishedAt", TurSEFieldType.DATE,
                        "[2026-10-15T00:00:00Z TO *]")));

        // Lucene, through the production document handler.
        TurSNSiteFieldService fieldService = mock(TurSNSiteFieldService.class);
        Map<String, TurSNSiteField> fieldMap = new LinkedHashMap<>();
        FIELD_TYPES.forEach((name, type) -> fieldMap.put(name,
                TurSNSiteField.builder().name(name).type(type).multiValued(0).build()));
        when(fieldService.toMap(any())).thenReturn(fieldMap);
        ByteBuffersDirectory directory = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()));
        TurLuceneInstance luceneInstance = mock(TurLuceneInstance.class);
        when(luceneInstance.getWriter()).thenReturn(writer);
        TurLuceneDocumentHandler handler = new TurLuceneDocumentHandler(fieldService, null);
        for (Object[] job : JOBS) {
            handler.indexing(luceneInstance, site, attributes(job));
        }
        writer.commit();
        luceneReader = DirectoryReader.open(writer);
        luceneSearcher = new IndexSearcher(luceneReader);
        luceneQueryBuilder = new TurLuceneQueryBuilder(fieldRepository, sortRepository, null, null, null,
                customFacets);

        // Elasticsearch, through Turing's own mapping and indexing calls.
        ES.start();
        String url = "http://" + ES.getHost() + ":" + ES.getMappedPort(9200);
        TurElasticsearchUtils.createIndex(url, INDEX, FIELD_TYPES);
        ElasticsearchClient client = client(url);
        esInstance = new TurElasticsearchInstance(client, URI.create(url).toURL(), INDEX);
        TurSNSiteRepository siteRepository = mock(TurSNSiteRepository.class);
        when(siteRepository.findByNameIgnoreCase("jobs")).thenReturn(Optional.of(site));
        elasticsearch = new TurElasticsearch(siteRepository, fieldRepository, sortRepository, customFacets);
        for (Object[] job : JOBS) {
            elasticsearch.indexing(esInstance, site, attributes(job));
        }
        client.indices().refresh(r -> r.index(INDEX));

        startSolr(fields, fieldRepository, sortRepository, fieldService, siteRepository);
    }

    /**
     * Solr, from the configset Turing provisions cores with: the {@code en} configset
     * the Turing Solr image ships, the core created by {@code TurSolrUtils.createCore}, each field
     * declared by {@code TurSolrUtils.addOrUpdateField} (provisioning's own call), and
     * documents indexed and searched through a real {@code TurSolr}, whose query
     * builder, document handler and result processor are the production ones.
     */
    private void startSolr(List<TurSNSiteFieldExt> fields, TurSNSiteFieldExtRepository fieldRepository,
            TurSNSiteCustomSortRepository sortRepository, TurSNSiteFieldService fieldService,
            TurSNSiteRepository siteRepository) throws Exception {
        // The configset the Turing Solr image ships (containers/solr/Dockerfile copies it to
        // /var/solr/data/configsets), on the latest Solr in user-managed mode: the solr:10
        // image starts in SolrCloud mode unless told otherwise.
        java.nio.file.Path configSet = java.nio.file.Path.of("..", "containers", "solr", "config", "solr",
                "configsets", "en").toAbsolutePath().normalize();
        solrContainer = new GenericContainer<>(DockerImageName.parse("solr:10.0.0"))
                .withExposedPorts(8983)
                .withCommand("solr-foreground", "--user-managed")
                .withCopyToContainer(org.testcontainers.utility.MountableFile.forHostPath(configSet, 0777),
                        "/tmp/configsets/en")
                .waitingFor(Wait.forHttp("/solr/admin/info/system").forStatusCode(200));
        solrContainer.start();
        // Installed as the solr user after start: copied before, /var/solr/data is created as
        // root and Solr cannot write the new core's properties.
        var install = solrContainer.execInContainer("bash", "-c",
                "mkdir -p /var/solr/data/configsets && cp -r /tmp/configsets/en /var/solr/data/configsets/en");
        assertThat(install.getExitCode()).as(install.getStderr()).isZero();
        String host = "http://" + solrContainer.getHost() + ":" + solrContainer.getMappedPort(8983);
        assertThat(com.viglet.turing.solr.TurSolrUtils.createCore(host, SOLR_CORE, "en")).as("Solr core").isTrue();
        com.viglet.turing.persistence.model.se.TurSEInstance seInstance =
                new com.viglet.turing.persistence.model.se.TurSEInstance();
        seInstance.setEndpointUrl(host);
        FIELD_TYPES.forEach((name, type) -> {
            if (!TurSNFieldName.ID.equals(name)) {
                com.viglet.turing.solr.TurSolrUtils.addOrUpdateField(
                        com.viglet.turing.solr.TurSolrFieldAction.ADD, seInstance, SOLR_CORE, name, type, true,
                        false);
            }
        });

        // "posted" -> "Recent": the same custom facet the other engines resolve, as Solr stores it.
        TurSNSiteFieldExt publishedAt = fields.stream().filter(f -> "publishedAt".equals(f.getName()))
                .findFirst().orElseThrow();
        com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet posted =
                com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet.builder()
                        .name("posted").turSNSiteFieldExt(publishedAt).build();
        posted.setItems(new HashSet<>(Set.of(
                com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem.builder()
                        .label("Recent").position(1)
                        .rangeStartDate(java.time.Instant.parse("2026-10-15T00:00:00Z")).build())));
        var customFacetRepository = mock(
                com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository.class);
        when(customFacetRepository.findByFieldExtsWithDetails(any())).thenReturn(List.of(posted));
        var decimalNormalizer = mock(com.viglet.turing.solr.TurDecimalFieldNormalizer.class);
        solr = new com.viglet.turing.solr.TurSolr(false, 0, fieldRepository, customFacetRepository, sortRepository,
                mock(com.viglet.turing.sn.tr.TurSNTargetingRules.class), fieldService,
                mock(com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository.class),
                mock(com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository.class),
                siteRepository, mock(com.viglet.turing.sn.TurSNFieldProcess.class), decimalNormalizer,
                mock(com.viglet.turing.sn.searchrule.TurSNSearchRuleEvaluator.class),
                mock(com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService.class),
                new com.viglet.turing.observability.TurSearchPipelineObservation(null));
        var solrClient = new org.apache.solr.client.solrj.impl.HttpJdkSolrClient.Builder(host + "/solr").build();
        solrInstance = new com.viglet.turing.solr.TurSolrInstance(solrClient, new java.net.URL(host + "/solr"),
                SOLR_CORE);
        for (Object[] job : JOBS) {
            solr.indexing(solrInstance, site, attributes(job));
        }
        solrClient.commit(SOLR_CORE);
    }

    @AfterAll
    void stop() throws Exception {
        luceneReader.close();
        ES.stop();
        if (solrContainer != null) {
            solrContainer.stop();
        }
    }

    private List<String> solr(TurSNSearchParams params) {
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("jobs", new TurSNConfig(),
                new TurSEParameters(params), java.util.Locale.US, URI.create("http://localhost/search"));
        return solr.retrieveSolrFromSN(solrInstance, context)
                .orElseThrow(() -> new AssertionError("Solr answered nothing"))
                .getResults().stream()
                .map(result -> String.valueOf(result.getFields().get(TurSNFieldName.ID)))
                .toList();
    }

    private static TurSNSearchParams request(Consumer<TurSNSearchParams> shape) {
        TurSNSearchParams params = new TurSNSearchParams();
        params.setQ("*");
        params.setRows(10);
        params.setP(1);
        shape.accept(params);
        return params;
    }

    private List<String> lucene(TurSNSearchParams params) throws Exception {
        TurSEParameters seParams = new TurSEParameters(params);
        Sort sort = luceneQueryBuilder.buildSort(site, seParams);
        var query = luceneQueryBuilder.buildQuery(site, seParams);
        ScoreDoc[] hits = sort == null ? luceneSearcher.search(query, 10).scoreDocs
                : luceneSearcher.search(query, 10, sort).scoreDocs;
        List<String> ids = new ArrayList<>();
        for (ScoreDoc hit : hits) {
            ids.add(luceneSearcher.storedFields().document(hit.doc).get(TurSNFieldName.ID));
        }
        return ids;
    }

    private List<String> elasticsearch(TurSNSearchParams params) {
        TurSNSiteSearchContext context = new TurSNSiteSearchContext("jobs", new TurSNConfig(),
                new TurSEParameters(params), java.util.Locale.US, URI.create("http://localhost/search"));
        return elasticsearch.retrieveElasticsearchFromSN(esInstance, context)
                .orElseThrow(() -> new AssertionError("Elasticsearch answered nothing"))
                .getResults().stream()
                .map(result -> result.getFields().get(TurSNFieldName.ID).toString())
                .toList();
    }

    static Stream<Arguments> filters() {
        return Stream.of(
                Arguments.of("one value", request(p -> p.setFq(List.of("sector:educacao"))),
                        Set.of("creche", "monitor")),
                Arguments.of("two values of one field intersect", request(p -> p.setFq(List.of(
                        "sector:educacao", "sector:saude"))), Set.of()),
                Arguments.of("fqItemOperator OR unions them", request(p -> {
                    p.setFq(List.of("sector:educacao", "sector:saude"));
                    p.setFqiOp(TurSNFilterQueryOperator.OR);
                }), Set.of("creche", "monitor", "enfermeira")),
                Arguments.of("fqOr is OR'd with the rest", request(p -> {
                    p.setFq(List.of("sector:educacao"));
                    p.setFqOr(List.of("salary:9000"));
                }), Set.of("creche", "monitor", "dados")),
                Arguments.of("fqAnd intersects with an open range", request(p -> {
                    p.setFqAnd(List.of("sector:educacao"));
                    p.setFq(List.of("salary:[1600 TO *]"));
                }), Set.of("creche")),
                Arguments.of("an exclusive numeric bound", request(p -> p.setFq(List.of(
                        "salary:{1800 TO 4000]"))), Set.of("creche", "enfermeira")),
                Arguments.of("an ISO date range", request(p -> p.setFq(List.of(
                        "publishedAt:[2026-10-01T00:00:00Z TO *]"))), Set.of("creche", "baba", "enfermeira")),
                Arguments.of("a custom facet selection", request(p -> p.setFq(List.of("posted:Recent"))),
                        Set.of("creche", "enfermeira")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filters")
    void bothEnginesFilterToTheSameDocuments(String name, TurSNSearchParams params, Set<String> expected)
            throws Exception {
        assertThat(new HashSet<>(lucene(params))).as("Lucene").isEqualTo(expected);
        assertThat(new HashSet<>(elasticsearch(params))).as("Elasticsearch").isEqualTo(expected);
        assertThat(new HashSet<>(solr(params))).as("Solr").isEqualTo(expected);
    }

    static Stream<Arguments> sorts() {
        return Stream.of(
                Arguments.of("newest", List.of("enfermeira", "creche", "baba", "monitor", "dados")),
                Arguments.of("salary:desc", List.of("dados", "enfermeira", "creche", "baba", "monitor")));
    }

    @ParameterizedTest(name = "sort={0}")
    @MethodSource("sorts")
    void bothEnginesSortTheSameWay(String sort, List<String> expected) throws Exception {
        TurSNSearchParams params = request(p -> p.setSort(sort));

        assertThat(lucene(params)).as("Lucene").containsExactlyElementsOf(expected);
        assertThat(elasticsearch(params)).as("Elasticsearch").containsExactlyElementsOf(expected);
        assertThat(solr(params)).as("Solr").containsExactlyElementsOf(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingPlacements")
    void bothEnginesPlaceJobsWithoutAnExpiryWhereTheCustomSortSays(String sort, boolean missingLast)
            throws Exception {
        List<String> dated = List.of("monitor", "enfermeira", "creche");
        Set<String> openEnded = Set.of("baba", "dados");
        TurSNSearchParams params = request(p -> p.setSort(sort));

        for (List<String> ids : List.of(lucene(params), elasticsearch(params), solr(params))) {
            List<String> datedPart = missingLast ? ids.subList(0, 3) : ids.subList(2, 5);
            List<String> openPart = missingLast ? ids.subList(3, 5) : ids.subList(0, 2);
            assertThat(datedPart).containsExactlyElementsOf(dated);
            assertThat(new HashSet<>(openPart)).isEqualTo(openEnded);
        }
    }

    static Stream<Arguments> missingPlacements() {
        return Stream.of(Arguments.of("closingSoonest", true), Arguments.of("openEndedFirst", false));
    }
}
