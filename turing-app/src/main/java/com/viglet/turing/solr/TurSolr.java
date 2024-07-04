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

import com.google.inject.Inject;
import com.viglet.turing.commons.se.TurSEFilterQueryParameters;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetEnum;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetRangeEnum;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetSortEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldSortEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;
import com.viglet.turing.persistence.utils.TurPersistenceUtils;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.result.TurSEGenericResults;
import com.viglet.turing.se.result.TurSEGroup;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.sn.tr.TurSNTargetingRuleMethod;
import com.viglet.turing.sn.tr.TurSNTargetingRules;
import com.viglet.turing.utils.TurSNSiteFieldUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.KeyValue;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.*;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.GroupParams;
import org.apache.solr.common.params.HighlightParams;
import org.apache.solr.common.params.MoreLikeThisParams;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.tika.utils.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
@Transactional
public class TurSolr {
    public static final String NEWEST = "newest";
    public static final String OLDEST = "oldest";
    public static final String ASC = "asc";
    public static final String COUNT = "count";
    public static final String SCORE = "score";
    public static final String VERSION = "_version_";
    public static final String BOOST = "boost";
    public static final String TURING_ENTITY = "turing_entity_";
    public static final String ID = "id";
    public static final String TITLE = "title";
    public static final String TYPE = "type";
    public static final String URL = "url";
    public static final String MORE_LIKE_THIS = "moreLikeThis";
    public static final String BOOST_QUERY = "bq";
    public static final String QUERY = "q";
    public static final String TRUE = "true";
    public static final String EDISMAX = "edismax";
    public static final String DEF_TYPE = "defType";

    public static final String AND = "AND";
    public static final String Q_OP = "q.op";
    public static final String RECENT_DATES = "{!func}recip(ms(NOW/DAY,%s),3.16e-11,1,1)";
    public static final String TUR_SUGGEST = "/tur_suggest";
    public static final String TUR_SPELL = "/tur_spell";
    public static final String FILTER_QUERY_OR = "{!tag=dt}";
    public static final String FACET_OR = "{!ex=dt}";
    public static final String PLUS_ONE = "+1";
    public static final String EMPTY = "";
    public static final String SOLR_DATE_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    public static final String INDEX = "index";
    public static final String ROWS = "rows";
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNTargetingRules turSNTargetingRules;
    private final TurSNSiteFieldUtils turSNSiteFieldUtils;
    private final TurSNRankingExpressionRepository turSNRankingExpressionRepository;
    private final TurSNRankingConditionRepository turSNRankingConditionRepository;
    private final TurSNSiteRepository turSNSiteRepository;

    @Inject
    public TurSolr(TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                   TurSNTargetingRules turSNTargetingRules,
                   TurSNSiteFieldUtils turSNSiteFieldUtils,
                   TurSNRankingExpressionRepository turSNRankingExpressionRepository,
                   TurSNRankingConditionRepository turSNRankingConditionRepository,
                   TurSNSiteRepository turSNSiteRepository) {
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNTargetingRules = turSNTargetingRules;
        this.turSNSiteFieldUtils = turSNSiteFieldUtils;
        this.turSNRankingExpressionRepository = turSNRankingExpressionRepository;
        this.turSNRankingConditionRepository = turSNRankingConditionRepository;
        this.turSNSiteRepository = turSNSiteRepository;
    }

    public long getDocumentTotal(TurSolrInstance turSolrInstance) {
        return executeSolrQuery(turSolrInstance, new SolrQuery().setQuery("*:*").setRows(0))
                .map(queryResponse ->
                        queryResponse.getResults().getNumFound()).orElse(0L);

    }

    public void indexing(TurSolrInstance turSolrInstance, TurSNSite turSNSite, Map<String, Object> attributes) {
        log.debug("Executing indexing ...");
        attributes.remove(SCORE);
        attributes.remove(VERSION);
        attributes.remove(BOOST);
        this.addDocument(turSolrInstance, turSNSite, attributes);
    }

    public void deIndexing(TurSolrInstance turSolrInstance, String id) {
        log.debug("Executing deIndexing ...");

        this.deleteDocument(turSolrInstance, id);
    }

    public void deIndexingByType(TurSolrInstance turSolrInstance, String type) {
        log.debug("Executing deIndexing by type {}...", type);
        this.deleteDocumentByType(turSolrInstance, type);

    }

    public void deleteDocument(TurSolrInstance turSolrInstance, String id) {
        try {
            turSolrInstance.getSolrClient().deleteById(id);
            turSolrInstance.getSolrClient().commit();
        } catch (SolrServerException | IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public void deleteDocumentByType(TurSolrInstance turSolrInstance, String type) {
        try {
            turSolrInstance.getSolrClient().deleteByQuery(TYPE + ":" + type);
            turSolrInstance.getSolrClient().commit();
        } catch (SolrServerException | IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    // Convert to String with concatenated attributes
    private String concatenateString(@SuppressWarnings("rawtypes") List list) {
        int i = 0;
        StringBuilder sb = new StringBuilder();
        for (Object valueItem : list) {
            sb.append(TurSolrField.convertFieldToString(valueItem));
            // Last Item
            if (i++ != list.size() - 1) sb.append(System.lineSeparator());
        }
        return sb.toString().trim();
    }

    public void addDocument(TurSolrInstance turSolrInstance, TurSNSite turSNSite, Map<String, Object> attributes) {
        Map<String, TurSNSiteField> turSNSiteFieldMap = turSNSiteFieldUtils.toMap(turSNSite);
        SolrInputDocument document = new SolrInputDocument();
        Optional.ofNullable(attributes).ifPresent(attr -> {
            attr.forEach((key, value) -> processAttribute(turSNSiteFieldMap, document, key, value));
            addSolrDocument(turSolrInstance, document);
        });
    }

    private void processAttribute(Map<String, TurSNSiteField> turSNSiteFieldMap, SolrInputDocument document,
                                  String key, Object attribute) {
        Optional.ofNullable(attribute).ifPresent(attr -> {
            switch (attr) {
                case Integer integer -> processInteger(document, key, integer);
                case JSONArray objects -> processJSONArray(turSNSiteFieldMap, document, key, objects);
                case ArrayList<?> arrayList -> processArrayList(turSNSiteFieldMap, document, key, arrayList);
                default -> processOtherTypes(document, key, attribute);
            }
        });
    }

    private void processOtherTypes(SolrInputDocument document, String key, Object attribute) {
        document.addField(key, TurSolrField.convertFieldToString(attribute));
    }

    private void processInteger(SolrInputDocument document, String key, Object attribute) {
        document.addField(key, attribute);
    }

    private void processJSONArray(Map<String, TurSNSiteField> turSNSiteFieldMap, SolrInputDocument document, String key,
                                  Object attribute) {
        JSONArray value = (JSONArray) attribute;
        if (key.startsWith(TURING_ENTITY) || isMultiValued(turSNSiteFieldMap, key)) {
            Optional.ofNullable(value).ifPresent(v ->
                    IntStream.range(0, value.length()).forEachOrdered(i -> document.addField(key, value.getString(i))));
        } else {
            document.addField(key, concatenateString(Optional.ofNullable(value)
                    .map(v -> IntStream.range(0, value.length())
                            .mapToObj(value::getString)
                            .collect(Collectors.toCollection(ArrayList::new)))
                    .orElse(new ArrayList<>())));
        }
    }

    private static boolean isMultiValued(Map<String, TurSNSiteField> turSNSiteFieldMap, String key) {
        return turSNSiteFieldMap.get(key) != null && turSNSiteFieldMap.get(key).getMultiValued() == 1;
    }

    private void processArrayList(Map<String, TurSNSiteField> turSNSiteFieldMap, SolrInputDocument document, String key,
                                  Object attribute) {
        @SuppressWarnings("rawtypes")
        List attributeList = (ArrayList) attribute;
        Optional.ofNullable(attributeList).ifPresent(values -> {
            if (key.startsWith(TURING_ENTITY)
                    || isMultiValued(turSNSiteFieldMap, key)) {
                for (Object valueItem : values) {
                    document.addField(key, TurSolrField.convertFieldToString(valueItem));
                }
            } else {
                document.addField(key, concatenateString(values));
            }
        });
    }

    private void addSolrDocument(TurSolrInstance turSolrInstance, SolrInputDocument document) {
        try {
            turSolrInstance.getSolrClient().add(document);
        } catch (SolrServerException | IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public SolrDocumentList solrResultAnd(TurSolrInstance turSolrInstance, Map<String, Object> attributes) {
        return executeSolrQuery(turSolrInstance,
                new SolrQuery()
                        .setQuery("*:*")
                        .setFilterQueries(attributes.entrySet().stream()
                                .map(entry -> entry.getKey() + ":\"" + entry.getValue() + "\"")
                                .toArray(String[]::new)))
                .map(QueryResponse::getResults).orElse(new SolrDocumentList());
    }

    public SpellCheckResponse autoComplete(TurSolrInstance turSolrInstance, String term) {
        return executeSolrQuery(turSolrInstance, new SolrQuery().setRequestHandler(TUR_SUGGEST).setQuery(term))
                .map(QueryResponse::getSpellCheckResponse).orElse(null);
    }

    public TurSESpellCheckResult spellCheckTerm(TurSolrInstance turSolrInstance, String term) {
        return executeSolrQuery(turSolrInstance, new SolrQuery().setRequestHandler(TUR_SPELL)
                .setQuery(term.replace("\"", EMPTY))).map(queryResponse ->
                        Optional.ofNullable(queryResponse.getSpellCheckResponse())
                                .map(spellCheckResponse -> {
                                    String correctedText = spellCheckResponse.getCollatedResult();
                                    if (!StringUtils.isEmpty(correctedText)) {
                                        return new TurSESpellCheckResult(true, correctedText);
                                    }
                                    return new TurSESpellCheckResult();
                                }).orElse(new TurSESpellCheckResult()))
                .orElse(new TurSESpellCheckResult());
    }

    public TurSEResult findById(TurSolrInstance turSolrInstance, TurSNSite turSNSite, String id) {
        SolrQuery query = new SolrQuery().setQuery(ID + ": \"" + id + "\"");
        return executeSolrQuery(turSolrInstance, query).map(queryResponse ->
                        queryResponse.getResults().stream().findFirst().map(solrDocument ->
                                createTurSEResult(getFieldExtMap(turSNSite), getRequiredFields(turSNSite),
                                        solrDocument, getHL(turSNSite, prepareQueryHL(turSNSite, query), queryResponse,
                                                solrDocument))).orElse(TurSEResult.builder().build()))
                .orElse(TurSEResult.builder().build());
    }

    private TurSESpellCheckResult prepareQueryAutoCorrection(TurSNSiteSearchContext turSNSiteSearchContext,
                                                             TurSNSite turSNSite, TurSolrInstance turSolrInstance) {
        TurSESpellCheckResult turSESpellCheckResult = spellCheckTerm(turSolrInstance,
                turSNSiteSearchContext.getTurSEParameters().getQuery());
        if (TurSNUtils.isAutoCorrectionEnabled(turSNSiteSearchContext, turSNSite)) {
            turSESpellCheckResult.setUsingCorrected(true);
            if (TurSNUtils.hasCorrectedText(turSESpellCheckResult)) {
                turSNSiteSearchContext.setUri(TurCommonsUtils.addOrReplaceParameter(turSNSiteSearchContext.getUri(),
                        QUERY, turSESpellCheckResult.getCorrectedText()));
            }
        } else {
            turSESpellCheckResult.setUsingCorrected(false);
        }
        return turSESpellCheckResult;
    }

    public Optional<TurSEResults> retrieveSolrFromSN(TurSolrInstance turSolrInstance,
                                                     TurSNSiteSearchContext context) {
        return turSNSiteRepository.findByName(context.getSiteName()).map(turSNSite -> {
            TurSESpellCheckResult turSESpellCheckResult = prepareQueryAutoCorrection(context,
                    turSNSite, turSolrInstance);
            TurSEParameters turSEParameters = context.getTurSEParameters();
            SolrQuery query = new SolrQuery();
            query.set(DEF_TYPE, EDISMAX);
            query.set(Q_OP, AND);
            setRows(turSNSite, turSEParameters);
            setSortEntry(turSNSite, query, turSEParameters);
            if (TurSNUtils.isAutoCorrectionEnabled(context, turSNSite)) {
                query.setQuery(TurSNUtils.hasCorrectedText(turSESpellCheckResult) ?
                        turSESpellCheckResult.getCorrectedText() : turSEParameters.getQuery());
            } else {
                query.setQuery(turSEParameters.getQuery());
            }
            if (!hasGroup(turSEParameters)) {
                query.setRows(turSEParameters.getRows())
                        .setStart(TurSolrUtils.firstRowPositionFromCurrentPage(turSEParameters));
            }
            prepareQueryFilterQuery(turSEParameters.getFilterQueries(), query, turSNSite);
            prepareQueryTargetingRules(context.getTurSNSitePostParamsBean(), query);
            if (hasGroup(turSEParameters)) {
                prepareGroup(turSEParameters, query);
            }
            prepareBoostQuery(turSNSite, query);
            return executeSolrQueryFromSN(turSolrInstance, turSNSite, turSEParameters, query,
                    prepareQueryMLT(turSNSite, query),
                    prepareQueryFacet(turSNSite, query, turSEParameters.getFilterQueries().getOperator()),
                    prepareQueryHL(turSNSite, query),
                    turSESpellCheckResult);
        }).orElse(Optional.empty());

    }

    private void prepareBoostQuery(TurSNSite turSNSite, SolrQuery query) {
        List<TurSNSiteFieldExt> turSNSiteFieldExtList = turSNSiteFieldExtRepository
                .findByTurSNSite(TurPersistenceUtils.orderByNameIgnoreCase(), turSNSite);
        query.set(BOOST_QUERY, turSNRankingExpressionRepository.findByTurSNSite(TurPersistenceUtils.orderByNameIgnoreCase(),
                turSNSite).stream().map(expression ->
                String.format(Locale.US, "%s^%.1f",
                        "(" + boostQueryAttributes(expression, turSNSiteFieldExtList) + ")",
                        expression.getWeight())).toArray(String[]::new));
    }

    private String boostQueryAttributes(TurSNRankingExpression expression, List<TurSNSiteFieldExt> turSNSiteFieldExtList) {
        return turSNRankingConditionRepository.findByTurSNRankingExpression(expression).stream().map(condition -> {
                    TurSNSiteFieldExt turSNSiteFieldExt = turSNSiteFieldExtList
                            .stream()
                            .filter(field -> field.getName().equals(condition.getAttribute()))
                            .findFirst().orElse(TurSNSiteFieldExt.builder().build());
                    if (turSNSiteFieldExt.getType().equals(TurSEFieldType.DATE) &&
                            condition.getValue().equalsIgnoreCase(ASC)) {
                        return String.format("_query_:\"" + RECENT_DATES + "\"",
                                condition.getAttribute());
                    }
                    return String.format("%s:%s", condition.getAttribute(), condition.getValue());
                })
                .collect(Collectors.joining(" AND "));
    }


    private void prepareGroup(TurSEParameters turSEParameters, SolrQuery query) {
        query.set(GroupParams.GROUP, TRUE)
                .set(GroupParams.GROUP_FIELD, turSEParameters.getGroup())
                .set(GroupParams.GROUP_LIMIT, turSEParameters.getRows())
                .set(GroupParams.GROUP_OFFSET, TurSolrUtils.firstRowPositionFromCurrentPage(turSEParameters))
                .set(ROWS, 100);

    }

    public boolean hasGroup(TurSEParameters turSEParameters) {
        return !StringUtils.isEmpty(turSEParameters.getGroup());
    }

    private Optional<TurSEResults> executeSolrQueryFromSN(TurSolrInstance turSolrInstance, TurSNSite turSNSite,
                                                          TurSEParameters turSEParameters, SolrQuery query,
                                                          List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList,
                                                          List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList,
                                                          List<TurSNSiteFieldExt> turSNSiteHlFieldExtList,
                                                          TurSESpellCheckResult turSESpellCheckResult) {
        if (enabledWildcardAlways(turSNSite) && isNotQueryExpression(query)) {
            addAWildcardInQuery(query);
        }
        return executeSolrQuery(turSolrInstance, query)
                .map(queryResponse -> getResults(turSolrInstance, turSNSite, turSEParameters, query,
                        turSNSiteMLTFieldExtList, turSNSiteFacetFieldExtList, turSNSiteHlFieldExtList,
                        turSESpellCheckResult,
                        getQueryResponseModified(turSolrInstance, turSNSite, query, queryResponse)));
    }

    private static QueryResponse getQueryResponseModified(TurSolrInstance turSolrInstance, TurSNSite turSNSite,
                                                          SolrQuery query, QueryResponse queryResponse) {
        return whenNoResultsUseWildcard(turSNSite, query, queryResponse) ?
                executeSolrQuery(turSolrInstance, query).orElse(queryResponse) :
                queryResponse;
    }

    private static Optional<QueryResponse> executeSolrQuery(TurSolrInstance turSolrInstance, SolrQuery query) {
        try {
            return Optional.ofNullable(turSolrInstance.getSolrClient().query(query));
        } catch (SolrServerException | IOException e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    private TurSEResults getResults(TurSolrInstance turSolrInstance, TurSNSite turSNSite,
                                    TurSEParameters turSEParameters, SolrQuery query,
                                    List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList,
                                    List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList,
                                    List<TurSNSiteFieldExt> turSNSiteHlFieldExtList,
                                    TurSESpellCheckResult turSESpellCheckResult,
                                    QueryResponse queryResponse) {
        TurSEResults turSEResults = TurSEResults.builder().build();
        turSEResultsParameters(turSEParameters, query, turSEResults, queryResponse);
        processSEResultsFacet(turSNSite, turSEResults, queryResponse, turSNSiteFacetFieldExtList);
        processResults(turSNSite, turSNSiteMLTFieldExtList, turSNSiteHlFieldExtList, turSEResults, queryResponse);
        processGroups(query, turSolrInstance, turSNSite, turSEParameters, turSNSiteMLTFieldExtList,
                turSNSiteHlFieldExtList, turSEResults, queryResponse);
        turSEResults.setSpellCheck(turSESpellCheckResult);
        return turSEResults;
    }

    private static void addAWildcardInQuery(SolrQuery query) {
        query.setQuery(query.getQuery().trim() + "*");
    }

    private static boolean whenNoResultsUseWildcard(TurSNSite turSNSite, SolrQuery query, QueryResponse queryResponse) {
        if (!enabledWildcardAlways(turSNSite)
                && enabledWildcardNoResults(turSNSite)
                && isNotQueryExpression(query)
                && noResultGroups(queryResponse)
                && noResults(queryResponse)
        ) {
            addAWildcardInQuery(query);
            return true;
        } else {
            return false;
        }
    }

    private static boolean enabledWildcardNoResults(TurSNSite turSNSite) {
        return turSNSite.getWildcardNoResults() != null
                && turSNSite.getWildcardNoResults() == 1;
    }

    private static boolean enabledWildcardAlways(TurSNSite turSNSite) {
        return turSNSite.getWildcardAlways() != null
                && turSNSite.getWildcardAlways() == 1;
    }

    private static boolean isNotQueryExpression(SolrQuery query) {
        return !query.getQuery().endsWith("*")
                || !query.getQuery().endsWith("\"")
                || !query.getQuery().endsWith("]")
                || !query.getQuery().endsWith(")");
    }

    private static boolean noResults(QueryResponse queryResponse) {
        return queryResponse.getResults() == null ||
                (queryResponse.getResults() != null && queryResponse.getResults().isEmpty());
    }

    private static boolean noResultGroups(QueryResponse queryResponse) {
        return queryResponse.getGroupResponse() == null ||
                (queryResponse.getGroupResponse() != null && queryResponse.getGroupResponse().getValues().isEmpty()) ||
                (queryResponse.getGroupResponse() != null && queryResponse.getGroupResponse().getValues().size() == 1 &&
                        queryResponse.getGroupResponse().getValues().getFirst().getValues().isEmpty());
    }

    private void processResults(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList,
                                List<TurSNSiteFieldExt> turSNSiteHlFieldExtList, TurSEResults turSEResults,
                                QueryResponse queryResponse) {
        List<TurSESimilarResult> similarResults = new ArrayList<>();
        turSEResults.setResults(addSolrDocumentsToSEResults(queryResponse.getResults(), turSNSite,
                turSNSiteMLTFieldExtList, queryResponse, similarResults, turSNSiteHlFieldExtList));
        setMLT(turSNSite, turSNSiteMLTFieldExtList, turSEResults, similarResults);
    }

    private void processGroups(SolrQuery query, TurSolrInstance turSolrInstance, TurSNSite turSNSite,
                               TurSEParameters turSEParameters, List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList,
                               List<TurSNSiteFieldExt> turSNSiteHlFieldExtList, TurSEResults turSEResults,
                               QueryResponse queryResponse) {
        if (hasGroup(turSEParameters) && queryResponse.getGroupResponse() != null) {
            List<TurSEGroup> turSEGroups = new ArrayList<>();
            queryResponse.getGroupResponse().getValues()
                    .forEach(groupCommand -> groupCommand.getValues().forEach(group ->
                                    Optional.ofNullable(group.getGroupValue()).ifPresent(g ->
                                            turSEGroups.add(setTurSEGroup(turSNSite, turSEParameters,
                                                    turSNSiteMLTFieldExtList, turSNSiteHlFieldExtList, queryResponse,
                                                    group)))
                            )
                    );
            if (enabledWildcardNoResults(turSNSite) && isNotQueryExpression(query)) {
                SolrQuery wildcardQuery = query.getCopy();
                addAWildcardInQuery(wildcardQuery);
                executeSolrQuery(turSolrInstance, wildcardQuery).ifPresent(queryResponseWildcard ->
                        queryResponseWildcard.getGroupResponse().getValues()
                                .forEach(groupCommand -> groupCommand.getValues().forEach(group ->
                                        seGroupsHasGroup(turSEGroups, group).ifPresentOrElse(turSEGroup -> {
                                                    if (turSEGroup.getResults().isEmpty() && !group.getResult()
                                                            .isEmpty()) {
                                                        turSEGroup.setResults(
                                                                addSolrDocumentsToSEResults(group.getResult(),
                                                                        turSNSite, turSNSiteMLTFieldExtList,
                                                                        queryResponse, null,
                                                                        turSNSiteHlFieldExtList));
                                                    }
                                                },
                                                () -> Optional.ofNullable(group.getGroupValue()).ifPresent(g ->
                                                        turSEGroups.add(setTurSEGroup(turSNSite,
                                                                turSEParameters, turSNSiteMLTFieldExtList,
                                                                turSNSiteHlFieldExtList, queryResponse,
                                                                group)))
                                        ))));
            }
            turSEResults.setGroups(turSEGroups);
        }
    }

    private static Optional<TurSEGroup> seGroupsHasGroup(List<TurSEGroup> turSEGroups, Group group) {
        return turSEGroups.stream().filter(o -> o.getName() != null && group.getGroupValue() != null
                && o.getName().equals(group.getGroupValue())).findFirst();
    }

    private TurSEGroup setTurSEGroup(TurSNSite turSNSite, TurSEParameters turSEParameters,
                                     List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList,
                                     List<TurSNSiteFieldExt> turSNSiteHlFieldExtList,
                                     QueryResponse queryResponse,
                                     Group group) {
        return TurSEGroup.builder()
                .name(group.getGroupValue())
                .numFound(group.getResult().getNumFound())
                .currentPage(turSEParameters.getCurrentPage())
                .limit(turSEParameters.getRows())
                .pageCount(getNumberOfPages(group.getResult().getNumFound(), turSEParameters.getRows()))
                .results(addSolrDocumentsToSEResults(group.getResult(), turSNSite,
                        turSNSiteMLTFieldExtList, queryResponse, null, turSNSiteHlFieldExtList)).build();
    }

    private void setMLT(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList, TurSEResults turSEResults,
                        List<TurSESimilarResult> similarResults) {
        if (hasMLT(turSNSite, turSNSiteMLTFieldExtList)) turSEResults.setSimilarResults(similarResults);
    }

    private void setRows(TurSNSite turSNSite, TurSEParameters turSEParameters) {
        if (turSEParameters.getRows() <= 0) turSEParameters.setRows(turSNSite.getRowsPerPage());
    }

    private void setSortEntry(TurSNSite turSNSite, SolrQuery query, TurSEParameters turSEParameters) {
        Optional.ofNullable(turSEParameters.getSort()).ifPresent(sort ->
                TurSolrUtils.getQueryKeyValue(sort).ifPresentOrElse(kv ->
                                query.setSort(kv.getKey(), kv.getValue().equals(ASC) ? ORDER.asc : ORDER.desc),
                        () -> {
                            if (sort.equalsIgnoreCase(NEWEST))
                                query.setSort(turSNSite.getDefaultDateField(), ORDER.desc);
                            else if (sort.equalsIgnoreCase(OLDEST))
                                query.setSort(turSNSite.getDefaultDateField(), ORDER.asc);
                        }));
    }

    private void turSEResultsParameters(TurSEParameters turSEParameters, SolrQuery query, TurSEResults turSEResults,
                                        QueryResponse queryResponse) {
        if (queryResponse.getResults() != null) {
            turSEResults.setNumFound(queryResponse.getResults().getNumFound());
            turSEResults.setStart(queryResponse.getResults().getStart());
        } else if (queryResponse.getGroupResponse() != null) {
            turSEResults.setNumFound(queryResponse.getGroupResponse()
                    .getValues().stream().mapToInt(GroupCommand::getMatches).sum());
        }
        turSEResults.setElapsedTime(queryResponse.getElapsedTime());
        turSEResults.setQTime(queryResponse.getQTime());
        turSEResults.setQueryString(query.getQuery());
        turSEResults.setSort(turSEParameters.getSort());
        turSEResults.setLimit(turSEParameters.getRows());
        turSEResults.setPageCount(getNumberOfPages(turSEResults));
        turSEResults.setCurrentPage(turSEParameters.getCurrentPage());
    }

    private int getNumberOfPages(TurSEGenericResults turSEGenericResults) {
        return getNumberOfPages(turSEGenericResults.getNumFound(), turSEGenericResults.getLimit());
    }

    private int getNumberOfPages(long numFound, int limit) {
        return (int) Math.ceil(numFound / (double) limit);
    }


    private boolean hasMLT(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList) {
        return turSNSite.getMlt() == 1 && !CollectionUtils.isEmpty(turSNSiteMLTFieldExtList);
    }

    private List<TurSEResult> addSolrDocumentsToSEResults(SolrDocumentList solrDocumentList, TurSNSite turSNSite,
                                                          List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList,
                                                          QueryResponse queryResponse,
                                                          List<TurSESimilarResult> similarResults,
                                                          List<TurSNSiteFieldExt> turSNSiteHlFieldExtList) {
        List<TurSEResult> results = new ArrayList<>();
        Optional.ofNullable(solrDocumentList).ifPresent(documents ->
                documents.forEach(document -> {
                    processSEResultsMLT(turSNSite, turSNSiteMLTFieldExtList, similarResults, document, queryResponse);
                    results.add(createTurSEResult(getFieldExtMap(turSNSite),
                            getRequiredFields(turSNSite), document,
                            getHL(turSNSite, turSNSiteHlFieldExtList, queryResponse, document)));
                }));
        return results;
    }

    private void processSEResultsMLT(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList,
                                     List<TurSESimilarResult> similarResults, SolrDocument document, QueryResponse queryResponse) {
        if (turSNSite.getMlt() == 1 && !turSNSiteMLTFieldExtList.isEmpty()) {
            @SuppressWarnings("rawtypes")
            SimpleOrderedMap mltResp = (SimpleOrderedMap) queryResponse.getResponse().get(MORE_LIKE_THIS);
            ((SolrDocumentList) mltResp.get((String) document.get(ID)))
                    .forEach(mltDocument -> similarResults.add(TurSESimilarResult.builder()
                            .id(TurSolrField.convertFieldToString(mltDocument.getFieldValue(ID)))
                            .title(TurSolrField.convertFieldToString(mltDocument.getFieldValue(TITLE)))
                            .type(TurSolrField.convertFieldToString(mltDocument.getFieldValue(TYPE)))
                            .url(TurSolrField.convertFieldToString(mltDocument.getFieldValue(URL)))
                            .build()));
        }
    }

    private void processSEResultsFacet(TurSNSite turSNSite, TurSEResults turSEResults, QueryResponse queryResponse,
                                       List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList) {
        if (wasFacetConfigured(turSNSite, turSNSiteFacetFieldExtList)) {
            List<TurSEFacetResult> facetRangeResults = setFacetRanges(queryResponse);
            List<TurSEFacetResult> facetResults = new ArrayList<>(facetRangeResults);
            facetResults.addAll(setFacetFields(queryResponse, facetRangeResults));
            turSEResults.setFacetResults(facetResults);
        }
    }

    private static List<TurSEFacetResult> setFacetRanges(QueryResponse queryResponse) {
        List<TurSEFacetResult> facetResults = new ArrayList<>();
        queryResponse.getFacetRanges().forEach(facet -> {
            TurSEFacetResult turSEFacetResult = new TurSEFacetResult();
            turSEFacetResult.setFacet(facet.getName());
            for (Object countItem : facet.getCounts()) {
                RangeFacet.Count count = (RangeFacet.Count) countItem;
                turSEFacetResult.add(count.getValue(),
                        new TurSEFacetResultAttr(count.getValue(), count.getCount()));
            }
            facetResults.add(turSEFacetResult);
        });
        return facetResults;
    }

    private static List<TurSEFacetResult> setFacetFields(QueryResponse queryResponse, List<TurSEFacetResult> facetRangeList) {
        List<TurSEFacetResult> facetResults = new ArrayList<>();
        queryResponse.getFacetFields().forEach(facet -> {
            if (facetRangeList.stream()
                    .noneMatch(rangeItem -> facet.getName().equals(rangeItem.getFacet()))) {
                TurSEFacetResult turSEFacetResult = new TurSEFacetResult();
                turSEFacetResult.setFacet(facet.getName());
                facet.getValues().forEach(item -> turSEFacetResult.add(item.getName(),
                        new TurSEFacetResultAttr(item.getName(), (int) item.getCount())));
                facetResults.add(turSEFacetResult);
            }
        });
        return facetResults;
    }

    private boolean wasFacetConfigured(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList) {
        return turSNSite.getFacet() == 1 && !CollectionUtils.isEmpty(turSNSiteFacetFieldExtList);
    }

    private void prepareQueryTargetingRules(TurSNSitePostParamsBean turSNSitePostParamsBean, SolrQuery query) {
        if (isTargetingRulesWithoutCondition(turSNSitePostParamsBean)) {
            targetingRulesWithoutCondition(turSNSitePostParamsBean, query);
        } else if (isTargetingRulesWithCondition(turSNSitePostParamsBean)) {
            targetingRulesWithCondition(turSNSitePostParamsBean, query);
        }
    }

    private static boolean isTargetingRulesWithCondition(TurSNSitePostParamsBean turSNSitePostParamsBean) {
        return !CollectionUtils.isEmpty(turSNSitePostParamsBean.getTargetingRulesWithCondition()) ||
                !CollectionUtils.isEmpty(turSNSitePostParamsBean.getTargetingRulesWithConditionAND()) ||
                !CollectionUtils.isEmpty(turSNSitePostParamsBean.getTargetingRulesWithConditionOR());
    }

    private static boolean isTargetingRulesWithoutCondition(TurSNSitePostParamsBean turSNSitePostParamsBean) {
        return !CollectionUtils.isEmpty(turSNSitePostParamsBean.getTargetingRules());
    }

    private void targetingRulesWithoutCondition(TurSNSitePostParamsBean turSNSitePostParamsBean, SolrQuery query) {
        if (!CollectionUtils.isEmpty(turSNSitePostParamsBean.getTargetingRules()))
            query.addFilterQuery(
                    turSNTargetingRules.ruleExpression(TurSNTargetingRuleMethod.AND,
                            turSNSitePostParamsBean.getTargetingRules()));
    }

    private void targetingRulesWithCondition(TurSNSitePostParamsBean turSNSitePostParamsBean, SolrQuery query) {
        Map<String, List<String>> formattedRulesAND = new HashMap<>();
        Map<String, List<String>> formattedRulesOR = new HashMap<>();
        Set<String> conditions = new HashSet<>();
        targetingRulesWithCondition(turSNSitePostParamsBean.getTargetingRulesWithCondition(), formattedRulesAND,
                conditions);
        targetingRulesWithCondition(turSNSitePostParamsBean.getTargetingRulesWithConditionAND(), formattedRulesAND,
                conditions);
        targetingRulesWithCondition(turSNSitePostParamsBean.getTargetingRulesWithConditionOR(), formattedRulesOR,
                conditions);
        List<String> rules = new ArrayList<>();
        conditions.forEach(condition -> {
            StringBuilder rule = new StringBuilder();
            boolean containAndRules = formattedRulesAND.containsKey(condition);
            boolean containOrRules = formattedRulesOR.containsKey(condition);
            if (containAndRules)
                rule.append(turSNTargetingRules.andMethod(formattedRulesAND.get(condition)));
            if (containOrRules) {
                rule.append(containAndRules ?
                        String.format(" AND (%s)", turSNTargetingRules.orMethod(formattedRulesOR.get(condition))) :
                        turSNTargetingRules.orMethod(formattedRulesOR.get(condition)));
            }
            rules.add(String.format("( %s AND ( %s ) )", condition, rule));
        });

        String targetingRuleQuery = String.format("%s OR (*:* NOT ( %s ) )",
                String.join(" OR ", rules),
                String.join(" OR ", conditions));
        query.addFilterQuery(targetingRuleQuery);
    }

    private void addFormattedRules(Map<String, List<String>> formattedRules, String key, List<String> value) {
        if (formattedRules.containsKey(key))
            formattedRules.get(key).addAll(value);
        else
            formattedRules.put(key, value);
    }

    private void targetingRulesWithCondition(Map<String, List<String>> targetingRulesWithCondition,
                                             Map<String, List<String>> formattedRules, Set<String> conditions) {
        if (!CollectionUtils.isEmpty(targetingRulesWithCondition)) {
            targetingRulesWithCondition.forEach((key, value) -> {
                conditions.add(key);
                addFormattedRules(formattedRules, key, value);
            });
        }
    }

    private void prepareQueryFilterQuery(TurSEFilterQueryParameters turSEFilterQueryParameters, SolrQuery query,
                                         TurSNSite turSNSite) {
        Optional.of(getFilterQueryMap(turSEFilterQueryParameters, turSNSite))
                .filter(fqMap -> !CollectionUtils.isEmpty(fqMap))
                .ifPresent(fqMap -> query.addFilterQuery(String.format("%s(%s)",
                        getFacetTypeConditionInFilterQuery(turSEFilterQueryParameters, turSNSite),
                        setFilterQueryString(setFilterQueryMapModified(turSNSite, fqMap)))));
    }

    @NotNull
    private Map<TurSNSiteFacetFieldEnum, List<String>> setFilterQueryMapModified(
            TurSNSite turSNSite,
            Map<TurSNSiteFacetFieldEnum, List<String>> filterQueryMap) {
        Map<TurSNSiteFacetFieldEnum, List<String>> filterQueryMapModified = new EnumMap<>(TurSNSiteFacetFieldEnum.class);
        getFilterQueryByDateRange(filterQueryMap, turSNSite).forEach((key, value) -> {
            if (filterQueryMapModified.containsKey(key)) {
                filterQueryMapModified.get(key).addAll(getFilterQueryValue(value));
            } else {
                filterQueryMapModified.put(key, getFilterQueryValue(value));
            }
        });
        return filterQueryMapModified;
    }

    @NotNull
    private static StringBuilder setFilterQueryString(Map<TurSNSiteFacetFieldEnum, List<String>> filterQueryMapModified) {
        StringBuilder filterQueryString = new StringBuilder();
        if (filterQueryMapModified.containsKey(TurSNSiteFacetFieldEnum.OR)) {
            filterQueryString.append(String.format("(%s)",
                    String.join(" OR ", filterQueryMapModified.get(TurSNSiteFacetFieldEnum.OR))));
            if (filterQueryMapModified.containsKey(TurSNSiteFacetFieldEnum.AND)) {
                filterQueryString.append(" AND ");
            }
        }
        if (filterQueryMapModified.containsKey(TurSNSiteFacetFieldEnum.AND)) {
            filterQueryString.append(String.format("%s",
                    String.join(" AND ", filterQueryMapModified.get(TurSNSiteFacetFieldEnum.AND))));
        }
        return filterQueryString;
    }

    @NotNull
    private static List<String> getFilterQueryValue(List<String> value) {
        return value.stream()
                .map(fq -> queryWithoutExpression(fq) ? addDoubleQuotesToValue(fq) : fq
                ).toList();
    }

    private Map<TurSNSiteFacetFieldEnum, List<String>> getFilterQueryMap(
            TurSEFilterQueryParameters filterQueries, TurSNSite turSNSite) {
        Map<TurSNSiteFacetFieldEnum, List<String>> fqMap = new EnumMap<>(TurSNSiteFacetFieldEnum.class);
        List<TurSNSiteFieldExt> enabledFacets = turSNSiteFieldExtRepository
                .findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1);
        Optional.ofNullable(filterQueries)
                .ifPresent(fq -> {
                    Optional.ofNullable(fq.getFq()).ifPresent(f -> f.forEach(fItem ->
                            addEnabledFacetItem(TurSNSiteFacetFieldEnum.DEFAULT, fItem, enabledFacets, fqMap, turSNSite,
                                    filterQueries.getOperator())));
                    Optional.ofNullable(fq.getAnd()).ifPresent(f -> f.forEach(fItem ->
                            addEnabledFacetItem(TurSNSiteFacetFieldEnum.AND, fItem, enabledFacets, fqMap, turSNSite,
                                    filterQueries.getOperator())));
                    Optional.ofNullable(fq.getOr()).ifPresent(f -> f.forEach(fItem ->
                            addEnabledFacetItem(TurSNSiteFacetFieldEnum.OR, fItem, enabledFacets, fqMap, turSNSite,
                                    filterQueries.getOperator())));
                });
        return fqMap;
    }

    private static void addEnabledFacetItem(TurSNSiteFacetFieldEnum turSNSiteFacetFieldEnum, String fq,
                                            List<TurSNSiteFieldExt> enabledFacets,
                                            Map<TurSNSiteFacetFieldEnum, List<String>> fqMap, TurSNSite turSNSite,
                                            TurSNFilterQueryOperator operator) {

        TurSolrUtils.getQueryKeyValue(fq).flatMap(kv ->
                        enabledFacets.stream()
                                .filter(facet -> facet.getName().equals(kv.getKey()))
                                .findFirst())
                .ifPresentOrElse(facet ->
                                addEnabledFacetItem(isFacetTypeDefault(turSNSiteFacetFieldEnum) ?
                                        getFacetType(facet, turSNSite, operator) :
                                        turSNSiteFacetFieldEnum, fqMap, fq),
                        () -> addEnabledFacetItem(getFacetType(turSNSite, operator), fqMap, fq));

    }

    private static void addEnabledFacetItem(TurSNSiteFacetFieldEnum facetType,
                                            Map<TurSNSiteFacetFieldEnum, List<String>> fqMap, String fq) {
        if (fqMap.containsKey(facetType)) {
            fqMap.get(facetType).add(fq);
        } else {
            List<String> list = new ArrayList<>();
            list.add(fq);
            fqMap.put(facetType, list);
        }
    }

    private static boolean isFacetTypeDefault(TurSNSiteFacetFieldEnum turSNSiteFacetFieldEnum) {
        return turSNSiteFacetFieldEnum == null || turSNSiteFacetFieldEnum.equals(TurSNSiteFacetFieldEnum.DEFAULT);
    }

    private static TurSNSiteFacetFieldEnum getFacetType(TurSNSiteFieldExt facet, TurSNSite turSNSite,
                                                        TurSNFilterQueryOperator operator) {
        return operatorIsNotEmpty(operator) ?
                getFaceTypeFromOperator(operator) :
                isFacetTypeDefault(facet.getFacetType()) ?
                        getFacetTypeFromSite(turSNSite) :
                        facet.getFacetType();

    }

    private static TurSNSiteFacetFieldEnum getFacetType(TurSNSite turSNSite,
                                                        TurSNFilterQueryOperator operator) {
        return operatorIsNotEmpty(operator) ? getFaceTypeFromOperator(operator) : getFacetTypeFromSite(turSNSite);

    }

    @NotNull
    private static TurSNSiteFacetFieldEnum getFaceTypeFromOperator(TurSNFilterQueryOperator operator) {
        return operator.equals(TurSNFilterQueryOperator.OR) ?
                TurSNSiteFacetFieldEnum.OR :
                TurSNSiteFacetFieldEnum.AND;
    }

    private static boolean operatorIsNotEmpty(TurSNFilterQueryOperator operator) {
        return operator != null && !operator.equals(TurSNFilterQueryOperator.NONE);
    }

    private static TurSNSiteFacetFieldEnum getFacetTypeFromSite(TurSNSite turSNSite) {
        return switch (turSNSite.getFacetType()) {
            case OR -> TurSNSiteFacetFieldEnum.OR;
            case null, default -> TurSNSiteFacetFieldEnum.AND;
        };
    }

    private Map<TurSNSiteFacetFieldEnum, List<String>> getFilterQueryByDateRange(
            Map<TurSNSiteFacetFieldEnum, List<String>> filterQueryMap,
            TurSNSite turSNSite) {
        List<TurSNSiteFieldExt> dateFacet = turSNSiteFieldExtRepository
                .findByTurSNSiteAndFacetAndEnabledAndType(turSNSite, 1, 1, TurSEFieldType.DATE);
        if (!dateFacet.isEmpty()) {
            Map<TurSNSiteFacetFieldEnum, List<String>> fqMapFormatted = new EnumMap<>(TurSNSiteFacetFieldEnum.class);
            filterQueryMap.forEach((conditional, fq) -> {
                if (fqMapFormatted.containsKey(conditional)) {
                    fqMapFormatted.get(conditional).addAll(setFilterQueryRangeValue(fq, dateFacet));
                } else {
                    fqMapFormatted.put(conditional,
                            setFilterQueryRangeValue(fq, dateFacet));
                }
            });
            return fqMapFormatted;
        } else {
            return filterQueryMap;
        }

    }

    @NotNull
    private static List<String> setFilterQueryRangeValue(List<String> filterQueries, List<TurSNSiteFieldExt> dateFacet) {
        return filterQueries.stream()
                .map(fq -> TurSolrUtils.getQueryKeyValue(fq)
                        .map(facetKv ->
                                dateFacet.stream()
                                        .filter(dateFacetItem -> facetKv.getKey().equals(dateFacetItem.getName()) &&
                                                isDateRangeFacet(dateFacetItem))
                                        .findFirst()
                                        .map(dateFacetItem ->
                                                setFilterQueryByRangeType(fq, facetKv, dateFacetItem.getFacetRange()))
                                        .orElse(fq))
                        .orElse(fq))
                .toList();
    }

    private static String setFilterQueryByRangeType(String fq, KeyValue<String, String> facetKv,
                                                    TurSNSiteFacetRangeEnum facetRange) {
        try {
            Date date = solrDateFormatter().parse(facetKv.getValue());
            return switch (facetRange) {
                case DAY -> setFilterQueryRangeDay(date, facetKv);
                case MONTH -> setFilterQueryRangeMonth(date, facetKv);
                case YEAR -> setFilterQueryRangeYear(date, facetKv);
                case DISABLED -> fq;
            };
        } catch (ParseException e) {
            log.error(e.getMessage(), e);
        }
        return fq;
    }

    @NotNull
    private static SimpleDateFormat solrDateFormatter() {
        return new SimpleDateFormat(SOLR_DATE_PATTERN, Locale.ENGLISH);
    }

    private static String setFilterQueryRangeDay(Date date, KeyValue<String, String> kv) {
        Calendar endOfDay = Calendar.getInstance();
        endOfDay.setTime(date);
        endOfDay.set(Calendar.HOUR, 23);
        endOfDay.set(Calendar.MINUTE, 59);
        endOfDay.set(Calendar.SECOND, 59);
        endOfDay.set(Calendar.MILLISECOND, 59);
        return setFilterQueryDateRange(kv, endOfDay);
    }

    private static String setFilterQueryDateRange(KeyValue<String, String> kv, Calendar endOfDay) {
        return String.format("%s:[ %s TO %s ]", kv.getKey(), kv.getValue(), solrDateFormatter().format(endOfDay.getTime()));
    }

    private static String setFilterQueryRangeYear(Date date, KeyValue<String, String> kv) {
        Calendar lastDateOfYear = Calendar.getInstance();
        lastDateOfYear.setTime(date);
        lastDateOfYear.set(Calendar.MONTH, 11);
        lastDateOfYear.set(Calendar.DAY_OF_MONTH, 31);
        lastDateOfYear.set(Calendar.HOUR, 23);
        lastDateOfYear.set(Calendar.MINUTE, 59);
        lastDateOfYear.set(Calendar.SECOND, 59);
        lastDateOfYear.set(Calendar.MILLISECOND, 59);
        return setFilterQueryDateRange(kv, lastDateOfYear);
    }

    private static String setFilterQueryRangeMonth(Date date, KeyValue<String, String> kv) {
        Calendar lastDateOfMonth = Calendar.getInstance();
        lastDateOfMonth.setTime(date);
        lastDateOfMonth.set(Calendar.DATE, lastDateOfMonth.getActualMaximum(Calendar.DATE));
        lastDateOfMonth.set(Calendar.HOUR, 23);
        lastDateOfMonth.set(Calendar.MINUTE, 59);
        lastDateOfMonth.set(Calendar.SECOND, 59);
        lastDateOfMonth.set(Calendar.MILLISECOND, 59);
        return setFilterQueryDateRange(kv, lastDateOfMonth);
    }

    private static boolean isDateRangeFacet(TurSNSiteFieldExt dateFacetItem) {
        return dateFacetItem.getType().equals(TurSEFieldType.DATE)
                && dateFacetItem.getFacetRange() != null
                && !dateFacetItem.getFacetRange().equals(TurSNSiteFacetRangeEnum.DISABLED);
    }

    private static String getFacetTypeConditionInFilterQuery(TurSEFilterQueryParameters turSEFilterQueryParameters,
                                                             TurSNSite turSNSite) {
        if (isOr(turSEFilterQueryParameters.getOperator(), turSNSite)) {
            return FILTER_QUERY_OR;
        }
        return EMPTY;
    }

    private static boolean isOr(TurSNFilterQueryOperator operator, TurSNSite turSNSite) {
        return (turSNSite.getFacetType() == TurSNSiteFacetEnum.OR
                && !operator.equals(TurSNFilterQueryOperator.AND))
                || operator.equals(TurSNFilterQueryOperator.OR);
    }

    @NotNull
    private static String addDoubleQuotesToValue(String q) {
        return TurSolrUtils.getQueryKeyValue(q)
                .map(kv -> String.format("%s:\"%s\"", kv.getKey(), kv.getValue()))
                .orElse(String.format("\"%s\"", q));
    }

    private static boolean queryWithoutExpression(String q) {
        String value = TurSolrUtils.getValueFromQuery(q);
        return !q.startsWith("(") && !value.startsWith("[") && !value.startsWith("(") && !value.endsWith("*");

    }


    private List<TurSNSiteFieldExt> prepareQueryMLT(TurSNSite turSNSite, SolrQuery query) {
        List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList = turSNSiteFieldExtRepository
                .findByTurSNSiteAndMltAndEnabled(turSNSite, 1, 1);
        if (hasMLT(turSNSite, turSNSiteMLTFieldExtList)) {
            query.set(MoreLikeThisParams.MLT, true)
                    .set(MoreLikeThisParams.MATCH_INCLUDE, true)
                    .set(MoreLikeThisParams.MIN_DOC_FREQ, 1)
                    .set(MoreLikeThisParams.MIN_TERM_FREQ, 1)
                    .set(MoreLikeThisParams.MIN_WORD_LEN, 7)
                    .set(MoreLikeThisParams.BOOST, false)
                    .set(MoreLikeThisParams.MAX_QUERY_TERMS, 1000)
                    .set(MoreLikeThisParams.SIMILARITY_FIELDS,
                            String.join(",", turSNSiteMLTFieldExtList.stream()
                                    .map(TurSNSiteFieldExt::getName)
                                    .toList()));
        }
        return turSNSiteMLTFieldExtList;
    }

    private static String setFacetTypeConditionInFacet(String query, TurSNSiteFieldExt turSNSiteFacetFieldExt,
                                                       TurSNSite turSNSite, TurSNFilterQueryOperator operator) {
        return switch (getFacetType(turSNSiteFacetFieldExt, turSNSite, operator)) {
            case OR -> FACET_OR.concat(query);
            case null, default -> query;
        };
    }

    private List<TurSNSiteFieldExt> prepareQueryFacet(TurSNSite turSNSite,
                                                      SolrQuery query, TurSNFilterQueryOperator operator) {
        List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList = turSNSiteFieldExtRepository
                .findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1);
        if (wasFacetConfigured(turSNSite, turSNSiteFacetFieldExtList)) {
            query.setFacet(true)
                    .setFacetLimit(turSNSite.getItemsPerFacet())
                    .setFacetMinCount(1)
                    .setFacetSort(facetSortIsEmptyOrCount(turSNSite) ? COUNT : INDEX);
            turSNSiteFacetFieldExtList.forEach(turSNSiteFacetFieldExt -> {
                setFacetSort(query, turSNSiteFacetFieldExt);
                if (isDateRangeFacet(turSNSiteFacetFieldExt))
                    addFacetRange(turSNSite, query, turSNSiteFacetFieldExt, operator);
                else addFacetField(turSNSite, query, turSNSiteFacetFieldExt, operator);
            });
        }
        return turSNSiteFacetFieldExtList;
    }

    private static void setFacetSort(SolrQuery query, TurSNSiteFieldExt turSNSiteFacetFieldExt) {
        Optional.of(turSNSiteFacetFieldExt)
                .map(TurSNSiteFieldExt::getFacetSort)
                .filter(field -> !turSNSiteFacetFieldExt.getFacetSort()
                        .equals(TurSNSiteFacetFieldSortEnum.DEFAULT))
                .ifPresent(field ->
                        query.set("f." + turSNSiteFacetFieldExt.getName() + ".facet.sort",
                                turSNSiteFacetFieldExt.getFacetSort()
                                        .equals(TurSNSiteFacetFieldSortEnum.ALPHABETICAL) ? INDEX : COUNT));
    }

    private static boolean facetSortIsEmptyOrCount(TurSNSite turSNSite) {
        return turSNSite.getFacetSort() == null || turSNSite.getFacetSort().equals(TurSNSiteFacetSortEnum.COUNT);
    }

    private static void addFacetField(TurSNSite turSNSite, SolrQuery query,
                                      TurSNSiteFieldExt turSNSiteFacetFieldExt, TurSNFilterQueryOperator operator) {
        query.addFacetField(setFacetTypeConditionInFacet(
                setEntityPrefix(turSNSiteFacetFieldExt)
                        .concat(turSNSiteFacetFieldExt.getName()), turSNSiteFacetFieldExt, turSNSite, operator));
    }

    private static void addFacetRange(TurSNSite turSNSite, SolrQuery query,
                                      TurSNSiteFieldExt turSNSiteFacetFieldExt, TurSNFilterQueryOperator operator) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.MONTH, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        query.set("f." + turSNSiteFacetFieldExt.getName() + ".facet.range.gap",
                PLUS_ONE + turSNSiteFacetFieldExt.getFacetRange());
        query.set("f." + turSNSiteFacetFieldExt.getName() + ".facet.range.start",
                solrDateFormatter().format(DateUtils.addYears(cal.getTime(), -100)));
        query.set("f." + turSNSiteFacetFieldExt.getName() + ".facet.range.end",
                solrDateFormatter().format(DateUtils.addYears(cal.getTime(), 100)));
        query.set("facet.range",
                setFacetTypeConditionInFacet(turSNSiteFacetFieldExt.getName(), turSNSiteFacetFieldExt, turSNSite, operator));
    }

    @NotNull
    private static String setEntityPrefix(TurSNSiteFieldExt turSNSiteFacetFieldExt) {
        return isNerOrThesaurus(turSNSiteFacetFieldExt.getSnType()) ? TURING_ENTITY : EMPTY;
    }

    private static boolean isNerOrThesaurus(TurSNFieldType snType) {
        return Collections.unmodifiableSet(EnumSet.of(TurSNFieldType.NER, TurSNFieldType.THESAURUS)).contains(snType);
    }

    public TurSEResults retrieveSolr(TurSolrInstance turSolrInstance, TurSEParameters turSEParameters,
                                     String defaultSortField) {
        SolrQuery query = new SolrQuery()
                .setQuery(turSEParameters.getQuery())
                .setRows(turSEParameters.getRows())
                .setStart(TurSolrUtils.firstRowPositionFromCurrentPage(turSEParameters));
        Optional.ofNullable(turSEParameters.getSort()).ifPresent(sort -> {
            if (sort.equalsIgnoreCase(NEWEST)) {
                query.setSort(defaultSortField, ORDER.desc);
            } else if (sort.equalsIgnoreCase(OLDEST)) {
                query.setSort(defaultSortField, ORDER.asc);
            }
        });
        filterQueryRequest(turSEParameters, query);
        return executeSolrQuery(turSolrInstance, query).map(queryResponse -> {
            TurSEResults turSEResults = TurSEResults.builder()
                    .spellCheck(spellCheckTerm(turSolrInstance, turSEParameters.getQuery()))
                    .results(queryResponse.getResults()
                            .stream().map(TurSolrUtils::createTurSEResultFromDocument).toList())
                    .build();
            turSEResultsParameters(turSEParameters, query, turSEResults, queryResponse);
            return turSEResults;
        }).orElse(TurSEResults.builder().build());
    }

    private static void filterQueryRequest(TurSEParameters turSEParameters, SolrQuery query) {
        Optional.ofNullable(turSEParameters.getFilterQueries().getFq()).ifPresent(filterQueries -> {
            String[] filterQueryArr = new String[filterQueries.size()];
            query.setFilterQueries(filterQueries.toArray(filterQueryArr));
        });
    }

    private List<TurSNSiteFieldExt> prepareQueryHL(TurSNSite turSNSite, SolrQuery query) {
        List<TurSNSiteFieldExt> turSNSiteHlFieldExtList = getHLFields(turSNSite);
        if (isHL(turSNSite, turSNSiteHlFieldExtList)) {
            StringBuilder hlFields = new StringBuilder();
            turSNSiteHlFieldExtList.forEach(turSNSiteHlFieldExt -> {
                if (!hlFields.isEmpty()) {
                    hlFields.append(",");
                }
                hlFields.append(turSNSiteHlFieldExt.getName());
            });
            query.setHighlight(true)
                    .setHighlightSnippets(1)
                    .setParam(HighlightParams.FIELDS, hlFields.toString())
                    .setParam(HighlightParams.FRAGSIZE, "0")
                    .setParam(HighlightParams.SIMPLE_PRE, turSNSite.getHlPre())
                    .setParam(HighlightParams.SIMPLE_POST, turSNSite.getHlPost());
        }
        return turSNSiteHlFieldExtList;
    }

    private List<TurSNSiteFieldExt> getHLFields(TurSNSite turSNSite) {
        return turSNSiteFieldExtRepository.findByTurSNSiteAndHlAndEnabled(turSNSite, 1, 1);
    }

    private Map<String, List<String>> getHL(TurSNSite
                                                    turSNSite, List<TurSNSiteFieldExt> turSNSiteHlFieldExtList,
                                            QueryResponse queryResponse, SolrDocument document) {
        return isHL(turSNSite, turSNSiteHlFieldExtList) ?
                queryResponse.getHighlighting().get(document.get(ID).toString()) : null;
    }

    private static boolean isHL(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteHlFieldExtList) {
        return turSNSite.getHl() == 1 && !CollectionUtils.isEmpty(turSNSiteHlFieldExtList);
    }

    private Map<String, TurSNSiteFieldExt> getFieldExtMap(TurSNSite turSNSite) {
        return turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite,
                1).stream().collect(Collectors
                .toMap(TurSNSiteFieldExt::getName, turSNSiteFieldExt -> turSNSiteFieldExt, (a, b) -> b));
    }

    private Map<String, Object> getRequiredFields(TurSNSite turSNSite) {
        return turSNSiteFieldExtRepository
                .findByTurSNSiteAndRequiredAndEnabled(turSNSite, 1, 1)
                .stream().filter(Objects::nonNull)
                .collect(Collectors
                        .toMap(TurSNSiteFieldExt::getName, TurSNSiteFieldExt::getDefaultValue, (a, b) -> b));
    }

    private TurSEResult createTurSEResult(Map<String, TurSNSiteFieldExt> fieldExtMap,
                                          Map<String, Object> requiredFields,
                                          SolrDocument document, Map<String, List<String>> hl) {
        addRequiredFieldsToDocument(requiredFields, document);
        return createTurSEResultFromDocument(fieldExtMap, document, hl);
    }

    @SuppressWarnings("unchecked")
    private TurSEResult createTurSEResultFromDocument(Map<String, TurSNSiteFieldExt> fieldExtMap,
                                                      SolrDocument document,
                                                      Map<String, List<String>> hl) {
        Map<String, Object> fields = new HashMap<>();
        for (String attribute : document.getFieldNames()) {
            Object attrValue = document.getFieldValue(attribute);
            if (isHLAttribute(fieldExtMap, hl, attribute)) {
                attrValue = hl.get(attribute).getFirst();
            }
            if (attribute != null && fields.containsKey(attribute)) {
                if (!(fields.get(attribute) instanceof List)) {
                    List<Object> attributeValues = new ArrayList<>();
                    attributeValues.add(fields.get(attribute));
                    attributeValues.add(attrValue);
                    fields.put(attribute, attributeValues);
                } else {
                    ((List<Object>) fields.get(attribute)).add(attrValue);
                }
            } else {
                fields.put(attribute, attrValue);
            }
        }
        return TurSEResult.builder().fields(fields).build();
    }

    private static boolean isHLAttribute(Map<String, TurSNSiteFieldExt> fieldExtMap, Map<String,
            List<String>> hl, String attribute) {
        return fieldExtMap.containsKey(attribute) &&
                (Collections.unmodifiableSet(EnumSet.of(TurSEFieldType.TEXT, TurSEFieldType.STRING))
                        .contains(fieldExtMap.get(attribute).getType())) &&
                hl != null && hl.containsKey(attribute);
    }

    private void addRequiredFieldsToDocument(Map<String, Object> requiredFields, SolrDocument document) {
        Arrays.stream(requiredFields.keySet().toArray()).map(String.class::cast).filter(requiredField ->
                        !document.containsKey(requiredField))
                .forEach(requiredField -> document.addField(requiredField, requiredFields.get(requiredField)));
    }
}
