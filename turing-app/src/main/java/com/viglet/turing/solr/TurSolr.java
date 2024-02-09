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
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;
import com.viglet.turing.persistence.utils.TurPesistenceUtils;
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
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrQuery.ORDER;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.GroupCommand;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SpellCheckResponse;
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
            if (i++ != list.size() - 1) {
                sb.append(System.lineSeparator());
            }
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
        if (key.startsWith(TURING_ENTITY)
                || (turSNSiteFieldMap.get(key) != null && turSNSiteFieldMap.get(key).getMultiValued() == 1)) {
            if (value != null) {
                IntStream.range(0, value.length()).forEachOrdered(i -> document.addField(key, value.getString(i)));
            }
        } else {
            ArrayList<String> listValues = new ArrayList<>();
            if (value != null) {
                listValues = IntStream.range(0, value.length())
                        .mapToObj(value::getString).collect(Collectors.toCollection(ArrayList::new));
            }
            document.addField(key, concatenateString(listValues));
        }
    }

    private void processArrayList(Map<String, TurSNSiteField> turSNSiteFieldMap, SolrInputDocument document, String key,
                                  Object attribute) {
        @SuppressWarnings("rawtypes")
        List attributeList = (ArrayList) attribute;
        Optional.ofNullable(attributeList).ifPresent(values -> {
            if (key.startsWith(TURING_ENTITY)
                    || (turSNSiteFieldMap.get(key) != null && turSNSiteFieldMap.get(key).getMultiValued() == 1)) {
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
        return executeSolrQuery(turSolrInstance, new SolrQuery().setRequestHandler("/tur_suggest").setQuery(term))
                .map(QueryResponse::getSpellCheckResponse).orElse(null);
    }

    public TurSESpellCheckResult spellCheckTerm(TurSolrInstance turSolrInstance, String term) {
        return executeSolrQuery(turSolrInstance, new SolrQuery().setRequestHandler("/tur_spell")
                .setQuery(term.replace("\"", ""))).map(queryResponse ->
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
            prepareQueryFilterQuery(turSEParameters, query, turSNSite);
            prepareQueryTargetingRules(TurSNTargetingRuleMethod.AND, context.getTurSNSitePostParamsBean(), query);
            if (hasGroup(turSEParameters)) {
                prepareGroup(turSEParameters, query);
            }
            prepareBoostQuery(turSNSite, query);
            return executeSolrQueryFromSN(turSolrInstance, turSNSite, turSEParameters, query,
                    prepareQueryMLT(turSNSite, query),
                    prepareQueryFacet(turSEParameters, turSNSite, query),
                    prepareQueryHL(turSNSite, query),
                    turSESpellCheckResult);
        }).orElse(Optional.empty());

    }

    private void prepareBoostQuery(TurSNSite turSNSite, SolrQuery query) {
        List<TurSNSiteFieldExt> turSNSiteFieldExtList = turSNSiteFieldExtRepository
                .findByTurSNSite(TurPesistenceUtils.orderByNameIgnoreCase(), turSNSite);
        query.set(BOOST_QUERY, turSNRankingExpressionRepository.findByTurSNSite(TurPesistenceUtils.orderByNameIgnoreCase(),
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
                .set(GroupParams.GROUP_OFFSET, TurSolrUtils.firstRowPositionFromCurrentPage(turSEParameters));

    }

    private boolean hasGroup(TurSEParameters turSEParameters) {
        return turSEParameters.getGroup() != null && !turSEParameters.getGroup().trim().isEmpty();
    }

    private Optional<TurSEResults> executeSolrQueryFromSN(TurSolrInstance turSolrInstance, TurSNSite turSNSite,
                                                          TurSEParameters turSEParameters, SolrQuery query,
                                                          List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList,
                                                          List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList,
                                                          List<TurSNSiteFieldExt> turSNSiteHlFieldExtList,
                                                          TurSESpellCheckResult turSESpellCheckResult) {
        return executeSolrQuery(turSolrInstance, query).map(queryResponse ->
                getResults(turSNSite, turSEParameters, query, turSNSiteMLTFieldExtList,
                        turSNSiteFacetFieldExtList, turSNSiteHlFieldExtList,
                        turSESpellCheckResult,
                        whenNoResultsUseWildcard(turSNSite, query, queryResponse) ?
                                executeSolrQuery(turSolrInstance, query).orElse(queryResponse) :
                                queryResponse));
    }

    private static Optional<QueryResponse> executeSolrQuery(TurSolrInstance turSolrInstance, SolrQuery query) {
        try {
            return Optional.ofNullable(turSolrInstance.getSolrClient().query(query));
        } catch (SolrServerException | IOException e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    private TurSEResults getResults(TurSNSite turSNSite, TurSEParameters turSEParameters, SolrQuery query,
                                    List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList,
                                    List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList,
                                    List<TurSNSiteFieldExt> turSNSiteHlFieldExtList,
                                    TurSESpellCheckResult turSESpellCheckResult,
                                    QueryResponse queryResponse) {
        TurSEResults turSEResults = new TurSEResults();
        turSEResultsParameters(turSEParameters, query, turSEResults, queryResponse);
        processSEResultsFacet(turSNSite, turSEResults, queryResponse, turSNSiteFacetFieldExtList);
        List<TurSESimilarResult> similarResults = new ArrayList<>();
        processGroups(turSNSite, turSEParameters, turSNSiteMLTFieldExtList, turSNSiteHlFieldExtList, turSEResults,
                queryResponse, similarResults);
        processResults(turSNSite, turSNSiteMLTFieldExtList, turSNSiteHlFieldExtList, turSEResults, queryResponse,
                similarResults);
        setMLT(turSNSite, turSNSiteMLTFieldExtList, turSEResults, similarResults);
        turSEResults.setSpellCheck(turSESpellCheckResult);
        return turSEResults;
    }

    private static void addAWildcardInQuery(SolrQuery query) {
        query.setQuery(query.getQuery().trim() + "*");
    }

    private static boolean whenNoResultsUseWildcard(TurSNSite turSNSite, SolrQuery query, QueryResponse queryResponse) {
        if (enabledWildcard(turSNSite)
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

    private static boolean enabledWildcard(TurSNSite turSNSite) {
        return turSNSite.getWhenNoResultsUseAsterisk() != null
                && turSNSite.getWhenNoResultsUseAsterisk() == 1;
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
                                QueryResponse queryResponse, List<TurSESimilarResult> similarResults) {
        turSEResults.setResults(addSolrDocumentsToSEResults(queryResponse.getResults(), turSNSite,
                turSNSiteMLTFieldExtList, queryResponse, similarResults, turSNSiteHlFieldExtList));
    }

    private void processGroups(TurSNSite turSNSite, TurSEParameters turSEParameters,
                               List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList, List<TurSNSiteFieldExt> turSNSiteHlFieldExtList,
                               TurSEResults turSEResults, QueryResponse queryResponse, List<TurSESimilarResult> similarResults) {
        if (hasGroup(turSEParameters) && queryResponse.getGroupResponse() != null) {
            List<TurSEGroup> turSEGroups = new ArrayList<>();
            queryResponse.getGroupResponse().getValues()
                    .forEach(groupCommand -> groupCommand.getValues().forEach(group -> {
                        TurSEGroup turSEGroup = new TurSEGroup();
                        turSEGroup.setName(group.getGroupValue());
                        turSEGroup.setNumFound(group.getResult().getNumFound());
                        turSEGroup.setCurrentPage(turSEParameters.getCurrentPage());
                        turSEGroup.setLimit(turSEParameters.getRows());
                        turSEGroup.setPageCount(getNumberOfPages(turSEGroup));
                        turSEGroup.setResults(addSolrDocumentsToSEResults(group.getResult(), turSNSite,
                                turSNSiteMLTFieldExtList, queryResponse, similarResults, turSNSiteHlFieldExtList));
                        turSEGroups.add(turSEGroup);
                    }));
            turSEResults.setGroups(turSEGroups);
        }
    }

    private void setMLT(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList, TurSEResults turSEResults,
                        List<TurSESimilarResult> similarResults) {
        if (hasMLT(turSNSite, turSNSiteMLTFieldExtList)) turSEResults.setSimilarResults(similarResults);
    }

    private void setRows(TurSNSite turSNSite, TurSEParameters turSEParameters) {
        if (turSEParameters.getRows() <= 0) turSEParameters.setRows(turSNSite.getRowsPerPage());
    }

    private void setSortEntry(TurSNSite turSNSite, SolrQuery query, TurSEParameters turSEParameters) {
        Optional.ofNullable(turSEParameters.getSort()).ifPresent(sort -> {
            String[] splitSort = sort.split(":");
            if (splitSort.length == 2)
                query.setSort(splitSort[0], splitSort[1].equals(ASC) ? ORDER.asc : ORDER.desc);
            else if (sort.equalsIgnoreCase(NEWEST))
                query.setSort(turSNSite.getDefaultDateField(), ORDER.desc);
            else if (sort.equalsIgnoreCase(OLDEST))
                query.setSort(turSNSite.getDefaultDateField(), ORDER.asc);
        });
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
        turSEResults.setqTime(queryResponse.getQTime());
        turSEResults.setQueryString(query.getQuery());
        turSEResults.setSort(turSEParameters.getSort());
        turSEResults.setLimit(turSEParameters.getRows());
        turSEResults.setPageCount(getNumberOfPages(turSEResults));
        turSEResults.setCurrentPage(turSEParameters.getCurrentPage());
    }

    private int getNumberOfPages(TurSEGenericResults turSEGenericResults) {
        return (int) Math.ceil(turSEGenericResults.getNumFound() / (double) turSEGenericResults.getLimit());
    }

    private boolean hasMLT(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList) {
        return turSNSite.getMlt() == 1 && turSNSiteMLTFieldExtList != null && !turSNSiteMLTFieldExtList.isEmpty();
    }

    private List<TurSEResult> addSolrDocumentsToSEResults(SolrDocumentList solrDocumentList, TurSNSite turSNSite,
                                                          List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList,
                                                          QueryResponse queryResponse,
                                                          List<TurSESimilarResult> similarResults,
                                                          List<TurSNSiteFieldExt> turSNSiteHlFieldExtList) {
        List<TurSEResult> results = new ArrayList<>();
        Optional.ofNullable(solrDocumentList).ifPresent(documents ->
                documents.forEach(document -> {
                    Map<String, List<String>> hl = getHL(turSNSite, turSNSiteHlFieldExtList, queryResponse, document);
                    processSEResultsMLT(turSNSite, turSNSiteMLTFieldExtList, similarResults, document, queryResponse);
                    results.add(createTurSEResult(getFieldExtMap(turSNSite),
                            getRequiredFields(turSNSite), document, hl));
                }));
        return results;
    }

    private void processSEResultsMLT(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList,
                                     List<TurSESimilarResult> similarResults, SolrDocument document, QueryResponse queryResponse) {
        if (turSNSite.getMlt() == 1 && !turSNSiteMLTFieldExtList.isEmpty()) {
            @SuppressWarnings("rawtypes")
            SimpleOrderedMap mltResp = (SimpleOrderedMap) queryResponse.getResponse().get(MORE_LIKE_THIS);
            SolrDocumentList mltDocumentList = (SolrDocumentList) mltResp.get((String) document.get(ID));
            mltDocumentList.forEach(mltDocument -> {
                TurSESimilarResult turSESimilarResult = new TurSESimilarResult();
                turSESimilarResult.setId(TurSolrField.convertFieldToString(mltDocument.getFieldValue(ID)));
                turSESimilarResult.setTitle(TurSolrField.convertFieldToString(mltDocument.getFieldValue(TITLE)));
                turSESimilarResult.setType(TurSolrField.convertFieldToString(mltDocument.getFieldValue(TYPE)));
                turSESimilarResult.setUrl(TurSolrField.convertFieldToString(mltDocument.getFieldValue(URL)));
                similarResults.add(turSESimilarResult);
            });
        }
    }

    private void processSEResultsFacet(TurSNSite turSNSite, TurSEResults turSEResults, QueryResponse queryResponse,
                                       List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList) {
        if (wasFacetConfigured(turSNSite, turSNSiteFacetFieldExtList)) {
            List<TurSEFacetResult> facetResults = new ArrayList<>();
            queryResponse.getFacetFields().forEach(facet -> {
                TurSEFacetResult turSEFacetResult = new TurSEFacetResult();
                turSEFacetResult.setFacet(facet.getName());
                facet.getValues().forEach(item -> turSEFacetResult.add(item.getName(),
                        new TurSEFacetResultAttr(item.getName(), (int) item.getCount())));
                facetResults.add(turSEFacetResult);
            });
            turSEResults.setFacetResults(facetResults);
        }
    }

    private boolean wasFacetConfigured(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList) {
        return turSNSite.getFacet() == 1 && turSNSiteFacetFieldExtList != null && !turSNSiteFacetFieldExtList.isEmpty();
    }

    private void prepareQueryTargetingRules(TurSNTargetingRuleMethod turSNTargetingRuleMethod,
                                            TurSNSitePostParamsBean turSNSitePostParamsBean, SolrQuery query) {
        if (!CollectionUtils.isEmpty(turSNSitePostParamsBean.getTargetingRules()))
            query.addFilterQuery(
                    turSNTargetingRules.run(turSNTargetingRuleMethod,
                            turSNSitePostParamsBean.getTargetingRules()));
        if (!CollectionUtils.isEmpty(turSNSitePostParamsBean.getTargetingRulesWithCondition())) {
            List<String> condition = new ArrayList<>();
            List<String> rules = new ArrayList<>();
            turSNSitePostParamsBean.getTargetingRulesWithCondition().forEach((key, value) -> {
                condition.add(key);
                rules.add(turSNTargetingRules.run(turSNTargetingRuleMethod, key, value));
            });
            query.setFilterQueries(String.format("%s OR (*:* NOT (%s))",
                    String.join(" OR ", rules),
                    String.join(" OR ", condition)));
        }
    }

    private void prepareQueryFilterQuery(TurSEParameters turSEParameters, SolrQuery query, TurSNSite turSNSite) {
        String facetTypeCondition = getFacetTypeConditionInQueryFilter(turSEParameters, turSNSite);
        if (!CollectionUtils.isEmpty(turSEParameters.getFilterQueries())) {
            if (isFilterQueryOr(turSEParameters, turSNSite)) {
                List<String> filterQueriesModified = turSEParameters.getFilterQueries().stream()
                        .map(q -> queryWithoutExpression(q) ? addDoubleQuotesToValue(q) : q
                        ).toList();
                query.setFilterQueries(
                        String.valueOf(String.format("%s(%s)", facetTypeCondition,
                                String.join(" OR ", filterQueriesModified))));
            } else {
                List<String> filterQueriesModified = turSEParameters.getFilterQueries().stream()
                        .map(q -> facetTypeCondition.concat(queryWithoutExpression(q) ? addDoubleQuotesToValue(q) : q)
                        ).toList();
                String[] filterQueryArr = new String[filterQueriesModified.size()];
                query.setFilterQueries(filterQueriesModified.toArray(filterQueryArr));
            }
        }
    }

    private static String getFacetTypeConditionInQueryFilter(TurSEParameters turSEParameters, TurSNSite turSNSite) {
        if (isOr(turSEParameters, turSNSite)) {
            return "{!tag=dt}";
        }
        return "";
    }

    private static boolean isOr(TurSEParameters turSEParameters, TurSNSite turSNSite) {
        return (turSNSite.getFacetType() == TurSNSiteFacetEnum.OR
                && !turSEParameters.getFqOperator().equals(TurSNFilterQueryOperator.AND))
                || turSEParameters.getFqOperator().equals(TurSNFilterQueryOperator.OR);
    }

    private static boolean isFilterQueryOr(TurSEParameters turSEParameters, TurSNSite turSNSite) {
        return !turSEParameters.getFqOperator().equals(TurSNFilterQueryOperator.AND)
                && (turSEParameters.getFqOperator().equals(TurSNFilterQueryOperator.OR)
                || turSNSite.getFacetType().equals(TurSNSiteFacetEnum.OR));
    }

    @NotNull
    private static String addDoubleQuotesToValue(String q) {
        String[] split = q.split(":", 2);
        split[1] = String.format("\"%s\"", split[1]);
        return String.join(":", split);
    }

    private static boolean queryWithoutExpression(String q) {
        String[] split = q.split(":", 2);
        return !q.startsWith("(") && !split[1].startsWith("[") && !split[1].startsWith("(") && !split[1].endsWith("*");
    }

    private List<TurSNSiteFieldExt> prepareQueryMLT(TurSNSite turSNSite, SolrQuery query) {
        List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList = turSNSiteFieldExtRepository
                .findByTurSNSiteAndMltAndEnabled(turSNSite, 1, 1);
        if (hasMLT(turSNSite, turSNSiteMLTFieldExtList)) {
            StringBuilder mltFields = new StringBuilder();
            turSNSiteMLTFieldExtList.forEach(turSNSiteMltFieldExt -> {
                if (!mltFields.isEmpty()) {
                    mltFields.append(",");
                }
                mltFields.append(turSNSiteMltFieldExt.getName());
            });
            query.set(MoreLikeThisParams.MLT, true)
                    .set(MoreLikeThisParams.MATCH_INCLUDE, true)
                    .set(MoreLikeThisParams.MIN_DOC_FREQ, 1)
                    .set(MoreLikeThisParams.MIN_TERM_FREQ, 1)
                    .set(MoreLikeThisParams.MIN_WORD_LEN, 7)
                    .set(MoreLikeThisParams.BOOST, false)
                    .set(MoreLikeThisParams.MAX_QUERY_TERMS, 1000)
                    .set(MoreLikeThisParams.SIMILARITY_FIELDS, mltFields.toString());
        }
        return turSNSiteMLTFieldExtList;
    }

    private static String setFacetTypeConditionInFacet(String query, TurSEParameters turSEParameters,
                                                       TurSNSite turSNSite) {
        return isOr(turSEParameters, turSNSite) ? "{!ex=dt}".concat(query) : query;
    }

    private List<TurSNSiteFieldExt> prepareQueryFacet(TurSEParameters turSEParameters, TurSNSite turSNSite,
                                                      SolrQuery query) {
        List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList = turSNSiteFieldExtRepository
                .findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1);
        if (wasFacetConfigured(turSNSite, turSNSiteFacetFieldExtList)) {
            query.setFacet(true)
                    .setFacetLimit(turSNSite.getItemsPerFacet())
                    .setFacetMinCount(1)
                    .setFacetSort(COUNT);
            turSNSiteFacetFieldExtList.forEach(turSNSiteFacetFieldExt ->
                    query.addFacetField(setFacetTypeConditionInFacet(
                            setEntityPrefix(turSNSiteFacetFieldExt)
                                    .concat(turSNSiteFacetFieldExt.getName()),
                            turSEParameters, turSNSite)

                    )
            );
        }
        return turSNSiteFacetFieldExtList;
    }

    @NotNull
    private static String setEntityPrefix(TurSNSiteFieldExt turSNSiteFacetFieldExt) {
        return isNerOrThesaurus(turSNSiteFacetFieldExt.getSnType()) ? TURING_ENTITY : "";
    }

    @NotNull
    private static String setCopyFieldSuffix(TurSNSiteFieldExt turSNSiteFacetFieldExt) {
        return turSNSiteFacetFieldExt.getType().equals(TurSEFieldType.TEXT) ? "_str" : "";
    }

    private static boolean isNerOrThesaurus(TurSNFieldType snType) {
        return snType == TurSNFieldType.NER || snType == TurSNFieldType.THESAURUS;
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
            TurSEResults turSEResults = new TurSEResults();
            turSEResultsParameters(turSEParameters, query, turSEResults, queryResponse);
            turSEResults.setSpellCheck(spellCheckTerm(turSolrInstance, turSEParameters.getQuery()));
            turSEResults.setResults(queryResponse.getResults()
                    .stream().map(TurSolrUtils::createTurSEResultFromDocument).collect(Collectors.toList()));
            return turSEResults;
        }).orElse(new TurSEResults());
    }

    private static void filterQueryRequest(TurSEParameters turSEParameters, SolrQuery query) {
        Optional.ofNullable(turSEParameters.getFilterQueries()).ifPresent(filterQueries -> {
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

    private Map<String, List<String>> getHL(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteHlFieldExtList,
                                            QueryResponse queryResponse, SolrDocument document) {
        return isHL(turSNSite, turSNSiteHlFieldExtList) ?
                queryResponse.getHighlighting().get(document.get(ID).toString()) : null;
    }

    private static boolean isHL(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteHlFieldExtList) {
        return turSNSite.getHl() == 1 && turSNSiteHlFieldExtList != null && !turSNSiteHlFieldExtList.isEmpty();
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
                fieldExtMap.get(attribute).getType() == TurSEFieldType.STRING &&
                hl != null && hl.containsKey(attribute);
    }

    private void addRequiredFieldsToDocument(Map<String, Object> requiredFields, SolrDocument document) {
        Arrays.stream(requiredFields.keySet().toArray()).map(requiredFieldObject ->
                        (String) requiredFieldObject).filter(requiredField ->
                        !document.containsKey(requiredField))
                .forEach(requiredField -> document.addField(requiredField, requiredFields.get(requiredField)));
    }
}
