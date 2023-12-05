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
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.repository.sn.TurSNSiteFieldExtRepository;
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
import org.apache.solr.common.params.MoreLikeThisParams;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.apache.tika.utils.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
@Transactional
public class TurSolr {
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNTargetingRules turSNTargetingRules;
    private final TurSNSiteFieldUtils turSNSiteFieldUtils;
    private final TurSNRankingExpressionRepository turSNRankingExpressionRepository;
    private final TurSNRankingConditionRepository turSNRankingConditionRepository;

    @Inject
    public TurSolr(TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                   TurSNTargetingRules turSNTargetingRules,
                   TurSNSiteFieldUtils turSNSiteFieldUtils,
                   TurSNRankingExpressionRepository turSNRankingExpressionRepository,
                   TurSNRankingConditionRepository turSNRankingConditionRepository) {
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNTargetingRules = turSNTargetingRules;
        this.turSNSiteFieldUtils = turSNSiteFieldUtils;
        this.turSNRankingExpressionRepository = turSNRankingExpressionRepository;
        this.turSNRankingConditionRepository = turSNRankingConditionRepository;
    }

    public long getDocumentTotal(TurSolrInstance turSolrInstance) {
        SolrQuery query = new SolrQuery();
        query.setQuery("*:*");
        query.setRows(0);
        try {
            return turSolrInstance.getSolrClient().query(query).getResults().getNumFound();
        } catch (SolrServerException | IOException e) {
            log.error(e.getMessage(), e);
        }

        return 0L;

    }

    public void indexing(TurSolrInstance turSolrInstance, TurSNSite turSNSite, Map<String, Object> attributes) {
        log.debug("Executing indexing ...");
        attributes.remove("score");
        attributes.remove("_version_");
        attributes.remove("boost");
        this.addDocument(turSolrInstance, turSNSite, attributes);
    }

    public void desindexing(TurSolrInstance turSolrInstance, String id) {
        log.debug("Executing desindexing ...");

        this.deleteDocument(turSolrInstance, id);
    }

    public void desindexingByType(TurSolrInstance turSolrInstance, String type) {
        log.debug("Executing desindexing by type {}...", type);
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
            turSolrInstance.getSolrClient().deleteByQuery("type:" + type);
            turSolrInstance.getSolrClient().commit();
        } catch (SolrServerException | IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    // Convert to String with concatenate attributes
    private String concatenateString(@SuppressWarnings("rawtypes") List list) {
        int i = 0;
        StringBuilder sb = new StringBuilder();
        for (Object valueItem : list) {
            sb.append(TurSolrField.convertFieldToString(valueItem));
            // Last Item
            if (i++ != list.size() - 1) {
                sb.append(System.getProperty("line.separator"));
            }
        }
        return sb.toString().trim();
    }

    public void addDocument(TurSolrInstance turSolrInstance, TurSNSite turSNSite, Map<String, Object> attributes) {
        Map<String, TurSNSiteField> turSNSiteFieldMap = turSNSiteFieldUtils.toMap(turSNSite);
        SolrInputDocument document = new SolrInputDocument();

        if (attributes != null) {
            attributes.entrySet().forEach(entry -> processAttribute(turSNSiteFieldMap, document, entry));
            addSolrDocument(turSolrInstance, document);
        }
    }

    private void processAttribute(Map<String, TurSNSiteField> turSNSiteFieldMap, SolrInputDocument document,
                                  Map.Entry<String, Object> entry) {
        String key = entry.getKey();
        Object attribute = entry.getValue();
        if (attribute != null) {
            if (attribute instanceof Integer) {
                processInteger(document, key, attribute);
            } else if (attribute instanceof org.json.JSONArray) {
                processJSONArray(turSNSiteFieldMap, document, key, attribute);
            } else if (attribute instanceof ArrayList) {
                processArrayList(turSNSiteFieldMap, document, key, attribute);
            } else {
                processeOtherTypes(document, key, attribute);
            }
        }
    }

    private void processeOtherTypes(SolrInputDocument document, String key, Object attribute) {
        String valueStr = TurSolrField.convertFieldToString(attribute);
        document.addField(key, valueStr);
    }

    private void processInteger(SolrInputDocument document, String key, Object attribute) {
        int intValue = (Integer) attribute;
        document.addField(key, intValue);
    }

    private void processJSONArray(Map<String, TurSNSiteField> turSNSiteFieldMap, SolrInputDocument document, String key,
                                  Object attribute) {
        JSONArray value = (JSONArray) attribute;
        if (key.startsWith("turing_entity_")
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
        ArrayList values = (ArrayList) attribute;
        if (values != null) {
            if (key.startsWith("turing_entity_")
                    || (turSNSiteFieldMap.get(key) != null && turSNSiteFieldMap.get(key).getMultiValued() == 1)) {
                for (Object valueItem : values) {
                    document.addField(key, TurSolrField.convertFieldToString(valueItem));
                }
            } else {
                document.addField(key, concatenateString(values));
            }
        }
    }

    public void addDocumentWithText(TurSolrInstance turSolrInstance, String currText) {
        SolrInputDocument document = new SolrInputDocument();
        document.addField("id", UUID.randomUUID());
        document.addField("text", currText);
        addSolrDocument(turSolrInstance, document);
    }

    private void addSolrDocument(TurSolrInstance turSolrInstance, SolrInputDocument document) {
        try {
            turSolrInstance.getSolrClient().add(document);
        } catch (SolrServerException | IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public SolrDocumentList solrResultAnd(TurSolrInstance turSolrInstance, Map<String, Object> attributes) {
        SolrQuery query = new SolrQuery();
        query.setQuery("*:*");
        query.setFilterQueries(attributes.entrySet().stream()
                .map(entry -> entry.getKey() + ":\"" + entry.getValue() + "\"")
                .toArray(String[]::new));
        try {
            return turSolrInstance.getSolrClient().query(query).getResults();
        } catch (IOException | SolrServerException e) {
            log.error(e.getMessage(), e);
        }
        return new SolrDocumentList();

    }

    public SpellCheckResponse autoComplete(TurSolrInstance turSolrInstance, String term) {
        SolrQuery query = new SolrQuery();
        query.setRequestHandler("/tur_suggest");
        query.setQuery(term);
        try {
            return turSolrInstance.getSolrClient().query(query).getSpellCheckResponse();
        } catch (IOException | SolrServerException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    public TurSESpellCheckResult spellCheckTerm(TurSolrInstance turSolrInstance, String term) {
        SolrQuery query = new SolrQuery();
        query.setRequestHandler("/tur_spell");
        query.setQuery(term.replace("\"", ""));

        try {
            QueryResponse queryResponse = turSolrInstance.getSolrClient().query(query);
            if (queryResponse.getSpellCheckResponse() != null) {
                String correctedText = queryResponse.getSpellCheckResponse().getCollatedResult();
                if (!StringUtils.isEmpty(correctedText)) {
                    return new TurSESpellCheckResult(true, correctedText);
                }
            }
        } catch (IOException | SolrServerException e) {
            log.error(e.getMessage(), e);
        }
        return new TurSESpellCheckResult();
    }

    public TurSEResult findById(TurSolrInstance turSolrInstance, TurSNSite turSNSite, String id) {

        Map<String, TurSNSiteFieldExt> fieldExtMap = getFieldExtMap(turSNSite);

        Map<String, Object> requiredFields = getRequiredFields(turSNSite);

        SolrQuery query = new SolrQuery();
        List<TurSNSiteFieldExt> turSNSiteHlFieldExts = prepareQueryHL(turSNSite, query);
        query.setQuery("id: \"" + id + "\"");

        TurSEResult turSEResult = null;
        try {
            QueryResponse queryResponse = turSolrInstance.getSolrClient().query(query);
            for (SolrDocument document : queryResponse.getResults()) {
                Map<String, List<String>> hl = getHL(turSNSite, turSNSiteHlFieldExts, queryResponse, document);
                turSEResult = createTurSEResult(fieldExtMap, requiredFields, document, hl);
            }
        } catch (SolrServerException | IOException e) {
            log.error(e.getMessage(), e);
        }

        return turSEResult;
    }

    public Optional<TurSEResults> retrieveSolrFromSN(TurSolrInstance turSolrInstance, TurSNSite turSNSite,
                                                     TurSNSiteSearchContext context, TurSESpellCheckResult turSESpellCheckResult) {

        TurSEParameters turSEParameters = context.getTurSEParameters();
        SolrQuery query = new SolrQuery();
        query.set("defType", "edismax");
        query.set("q.op", "AND");
        setRows(turSNSite, turSEParameters);
        setSortEntry(turSNSite, query, turSEParameters);
        if (TurSNUtils.isAutoCorrectionEnabled(context, turSNSite)) {
            if (TurSNUtils.hasCorrectedText(turSESpellCheckResult)) {
                query.setQuery(turSESpellCheckResult.getCorrectedText());
            } else {
                query.setQuery(turSEParameters.getQuery());
            }
        } else {
            query.setQuery(turSEParameters.getQuery());
        }
        if (!hasGroup(turSEParameters)) {
            query.setRows(turSEParameters.getRows());
            query.setStart(TurSolrUtils.firstRowPositionFromCurrentPage(turSEParameters));
        }
        prepareQueryFilterQuery(turSEParameters, query);
        prepareQueryTargetingRules(context.getTurSNSitePostParamsBean(), query);
        if (hasGroup(turSEParameters)) {
            prepareGroup(turSEParameters, query);
        }
        prepareBoostQuery(turSNSite, query);
        return executeSolrQueryFromSN(turSolrInstance, turSNSite, turSEParameters, query,
                prepareQueryMLT(turSNSite, query),
                prepareQueryFacet(turSNSite, query),
                prepareQueryHL(turSNSite, query),
                turSESpellCheckResult);
    }

    private void prepareBoostQuery(TurSNSite turSNSite, SolrQuery query) {
        List<TurSNSiteFieldExt> turSNSiteFieldExts = turSNSiteFieldExtRepository
                .findByTurSNSite(TurPesistenceUtils.orderByNameIgnoreCase(), turSNSite);
        query.set("bq", turSNRankingExpressionRepository.findByTurSNSite(TurPesistenceUtils.orderByNameIgnoreCase(),
                turSNSite).stream().map(expression ->
                String.format(Locale.US, "%s^%.1f",
                        "(" + boostQueryAttributes(expression, turSNSiteFieldExts) + ")",
                        expression.getWeight())).toArray(String[]::new));
    }

    private String boostQueryAttributes(TurSNRankingExpression expression, List<TurSNSiteFieldExt> turSNSiteFieldExts) {
        return turSNRankingConditionRepository.findByTurSNRankingExpression(expression).stream().map(condition -> {
                    TurSNSiteFieldExt turSNSiteFieldExt = turSNSiteFieldExts
                            .stream()
                            .filter(field -> field.getName().equals(condition.getAttribute()))
                            .findFirst().orElse(new TurSNSiteFieldExt());
                    if (turSNSiteFieldExt.getType().equals(TurSEFieldType.DATE) &&
                            condition.getValue().equalsIgnoreCase("asc")) {
                        return String.format("_query_:\"{!func}recip(ms(NOW/DAY,%s),3.16e-11,1,1)\"",
                                condition.getAttribute());
                    }
                    return String.format("%s:%s", condition.getAttribute(), condition.getValue());
                })
                .collect(Collectors.joining(" AND "));
    }


    private void prepareGroup(TurSEParameters turSEParameters, SolrQuery query) {

        query.set("group", "true");
        query.set("group.field", turSEParameters.getGroup());
        query.set("group.limit", turSEParameters.getRows());
        query.set("group.offset", TurSolrUtils.firstRowPositionFromCurrentPage(turSEParameters));

    }

    private boolean hasGroup(TurSEParameters turSEParameters) {
        return turSEParameters.getGroup() != null && !turSEParameters.getGroup().trim().isEmpty();
    }

    private Optional<TurSEResults> executeSolrQueryFromSN(TurSolrInstance turSolrInstance, TurSNSite turSNSite,
                                                          TurSEParameters turSEParameters, SolrQuery query, List<TurSNSiteFieldExt> turSNSiteMLTFieldExts,
                                                          List<TurSNSiteFieldExt> turSNSiteFacetFieldExts, List<TurSNSiteFieldExt> turSNSiteHlFieldExts,
                                                          TurSESpellCheckResult turSESpellCheckResult) {
        TurSEResults turSEResults = new TurSEResults();
        try {
            QueryResponse queryResponse = turSolrInstance.getSolrClient().query(query);
            turSEResultsParameters(turSEParameters, query, turSEResults, queryResponse);
            processSEResultsFacet(turSNSite, turSEResults, queryResponse, turSNSiteFacetFieldExts);
            List<TurSESimilarResult> similarResults = new ArrayList<>();
            processGroups(turSNSite, turSEParameters, turSNSiteMLTFieldExts, turSNSiteHlFieldExts, turSEResults,
                    queryResponse, similarResults);
            processResults(turSNSite, turSNSiteMLTFieldExts, turSNSiteHlFieldExts, turSEResults, queryResponse,
                    similarResults);
            setMLT(turSNSite, turSNSiteMLTFieldExts, turSEResults, similarResults);
            turSEResults.setSpellCheck(turSESpellCheckResult);
            return Optional.of(turSEResults);
        } catch (IOException | SolrServerException e) {
            log.error(e.getMessage(), e);
        }
        return Optional.empty();
    }

    private void processResults(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExts,
                                List<TurSNSiteFieldExt> turSNSiteHlFieldExts, TurSEResults turSEResults, QueryResponse queryResponse,
                                List<TurSESimilarResult> similarResults) {
        turSEResults.setResults(addSolrDocumentsToSEResults(queryResponse.getResults(), turSNSite,
                turSNSiteMLTFieldExts, queryResponse, similarResults, turSNSiteHlFieldExts));
    }

    private void processGroups(TurSNSite turSNSite, TurSEParameters turSEParameters,
                               List<TurSNSiteFieldExt> turSNSiteMLTFieldExts, List<TurSNSiteFieldExt> turSNSiteHlFieldExts,
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
                                turSNSiteMLTFieldExts, queryResponse, similarResults, turSNSiteHlFieldExts));
                        turSEGroups.add(turSEGroup);

                    }));
            turSEResults.setGroups(turSEGroups);
        }
    }

    private void setMLT(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExts, TurSEResults turSEResults,
                        List<TurSESimilarResult> similarResults) {
        if (hasMLT(turSNSite, turSNSiteMLTFieldExts)) {
            turSEResults.setSimilarResults(similarResults);
        }
    }

    private void setRows(TurSNSite turSNSite, TurSEParameters turSEParameters) {
        if (turSEParameters.getRows() <= 0) {
            turSEParameters.setRows(turSNSite.getRowsPerPage());
        }
    }

    private void setSortEntry(TurSNSite turSNSite, SolrQuery query, TurSEParameters turSEParameters) {
        SimpleEntry<String, String> sortEntry = null;

        if (turSEParameters.getSort() != null) {
            String[] splitSort = turSEParameters.getSort().split(":");
            if (splitSort.length == 2) {
                query.setSort(splitSort[0], splitSort[1].equals("asc") ? ORDER.asc : ORDER.desc);
            } else {
                if (turSEParameters.getSort().equalsIgnoreCase("newest")) {
                    sortEntry = new SimpleEntry<>(turSNSite.getDefaultDateField(), "desc");
                } else if (turSEParameters.getSort().equalsIgnoreCase("oldest")) {
                    sortEntry = new SimpleEntry<>(turSNSite.getDefaultDateField(), "asc");
                }

                if (sortEntry != null) {
                    query.setSort(sortEntry.getKey(), sortEntry.getValue().equals("asc") ? ORDER.asc : ORDER.desc);
                }
            }
        }
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

    private boolean hasMLT(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExts) {
        return turSNSite.getMlt() == 1 && turSNSiteMLTFieldExts != null && !turSNSiteMLTFieldExts.isEmpty();
    }

    private List<TurSEResult> addSolrDocumentsToSEResults(SolrDocumentList solrDocumentList, TurSNSite turSNSite,
                                                          List<TurSNSiteFieldExt> turSNSiteMLTFieldExts,
                                                          QueryResponse queryResponse,
                                                          List<TurSESimilarResult> similarResults,
                                                          List<TurSNSiteFieldExt> turSNSiteHlFieldExts) {
        List<TurSEResult> results = new ArrayList<>();
        if (solrDocumentList != null) {
            solrDocumentList.forEach(document -> {
                Map<String, List<String>> hl = getHL(turSNSite, turSNSiteHlFieldExts, queryResponse, document);
                processSEResultsMLT(turSNSite, turSNSiteMLTFieldExts, similarResults, document, queryResponse);
                results.add(createTurSEResult(getFieldExtMap(turSNSite),
                        getRequiredFields(turSNSite), document, hl));
            });
        }
        return results;
    }

    private void processSEResultsMLT(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExts,
                                     List<TurSESimilarResult> similarResults, SolrDocument document, QueryResponse queryResponse) {
        if (turSNSite.getMlt() == 1 && !turSNSiteMLTFieldExts.isEmpty()) {
            @SuppressWarnings("rawtypes")
            SimpleOrderedMap mltResp = (SimpleOrderedMap) queryResponse.getResponse().get("moreLikeThis");
            SolrDocumentList mltDocumentList = (SolrDocumentList) mltResp.get((String) document.get("id"));
            mltDocumentList.forEach(mltDocument -> {
                TurSESimilarResult turSESimilarResult = new TurSESimilarResult();
                turSESimilarResult.setId(TurSolrField.convertFieldToString(mltDocument.getFieldValue("id")));
                turSESimilarResult.setTitle(TurSolrField.convertFieldToString(mltDocument.getFieldValue("title")));
                turSESimilarResult.setType(TurSolrField.convertFieldToString(mltDocument.getFieldValue("type")));
                turSESimilarResult.setUrl(TurSolrField.convertFieldToString(mltDocument.getFieldValue("url")));
                similarResults.add(turSESimilarResult);
            });
        }
    }

    private void processSEResultsFacet(TurSNSite turSNSite, TurSEResults turSEResults, QueryResponse queryResponse,
                                       List<TurSNSiteFieldExt> turSNSiteFacetFieldExts) {
        if (wasFacetConfigured(turSNSite, turSNSiteFacetFieldExts)) {
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

    private boolean wasFacetConfigured(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteFacetFieldExts) {
        return turSNSite.getFacet() == 1 && turSNSiteFacetFieldExts != null && !turSNSiteFacetFieldExts.isEmpty();
    }

    private void prepareQueryTargetingRules(TurSNSitePostParamsBean turSNSitePostParamsBean, SolrQuery query) {
        if (turSNSitePostParamsBean.getTargetingRules() != null
                && !turSNSitePostParamsBean.getTargetingRules().isEmpty())
            query.addFilterQuery(
                    turSNTargetingRules.run(TurSNTargetingRuleMethod.AND, turSNSitePostParamsBean.getTargetingRules()));
    }

    private void prepareQueryFilterQuery(TurSEParameters turSEParameters, SolrQuery query) {
        if (turSEParameters.getFilterQueries() != null && !turSEParameters.getFilterQueries().isEmpty()) {
            List<String> filterQueriesModified = turSEParameters.getFilterQueries().stream()
                    .map(q -> queryWithoutExpression(q) ? addDoubleQuotesToValue(q) : q).toList();
            if (isFilterQueryOR(turSEParameters)) {
                query.setFilterQueries(
                        String.valueOf(String.format("(%s)", String.join(" OR ", filterQueriesModified))));
            } else {
                String[] filterQueryArr = new String[filterQueriesModified.size()];
                query.setFilterQueries(filterQueriesModified.toArray(filterQueryArr));
            }
        }
    }

    private static boolean isFilterQueryOR(TurSEParameters turSEParameters) {
        return turSEParameters.getFqOperator().equals(TurSNFilterQueryOperator.OR);
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
        List<TurSNSiteFieldExt> turSNSiteMLTFieldExts = turSNSiteFieldExtRepository
                .findByTurSNSiteAndMltAndEnabled(turSNSite, 1, 1);
        if (hasMLT(turSNSite, turSNSiteMLTFieldExts)) {

            StringBuilder mltFields = new StringBuilder();
            turSNSiteMLTFieldExts.forEach(turSNSiteMltFieldExt -> {
                if (!mltFields.isEmpty()) {
                    mltFields.append(",");
                }
                mltFields.append(turSNSiteMltFieldExt.getName());
            });

            query.set(MoreLikeThisParams.MLT, true);
            query.set(MoreLikeThisParams.MATCH_INCLUDE, true);
            query.set(MoreLikeThisParams.MIN_DOC_FREQ, 1);
            query.set(MoreLikeThisParams.MIN_TERM_FREQ, 1);
            query.set(MoreLikeThisParams.MIN_WORD_LEN, 7);
            query.set(MoreLikeThisParams.BOOST, false);
            query.set(MoreLikeThisParams.MAX_QUERY_TERMS, 1000);
            query.set(MoreLikeThisParams.SIMILARITY_FIELDS, mltFields.toString());
        }
        return turSNSiteMLTFieldExts;
    }

    private List<TurSNSiteFieldExt> prepareQueryFacet(TurSNSite turSNSite, SolrQuery query) {
        // Facet
        List<TurSNSiteFieldExt> turSNSiteFacetFieldExts = turSNSiteFieldExtRepository
                .findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1);

        if (wasFacetConfigured(turSNSite, turSNSiteFacetFieldExts)) {
            query.setFacet(true);
            query.setFacetLimit(turSNSite.getItemsPerFacet());
            query.setFacetMinCount(1);
            query.setFacetSort("count");

            turSNSiteFacetFieldExts.forEach(turSNSiteFacetFieldExt -> {
                TurSNFieldType snType = turSNSiteFacetFieldExt.getSnType();
                if (snType == TurSNFieldType.NER || snType == TurSNFieldType.THESAURUS) {
                    query.addFacetField(String.format("turing_entity_%s", turSNSiteFacetFieldExt.getName()));
                } else {
                    query.addFacetField(turSNSiteFacetFieldExt.getName());
                }
            });

        }
        return turSNSiteFacetFieldExts;
    }

    public TurSEResults retrieveSolr(TurSolrInstance turSolrInstance, TurSEParameters turSEParameters,
                                     String defaultSortField) {


        SimpleEntry<String, String> sortEntry = null;
        if (turSEParameters.getSort() != null) {
            if (turSEParameters.getSort().equalsIgnoreCase("newest")) {
                sortEntry = new SimpleEntry<>(defaultSortField, "desc");
            } else if (turSEParameters.getSort().equalsIgnoreCase("oldest")) {
                sortEntry = new SimpleEntry<>(defaultSortField, "asc");
            }
        }
        SolrQuery query = new SolrQuery();

        query.setQuery(turSEParameters.getQuery());

        if (sortEntry != null) {
            query.setSort(sortEntry.getKey(), sortEntry.getValue().equals("asc") ? ORDER.asc : ORDER.desc);
        }

        query.setRows(turSEParameters.getRows());
        query.setStart(TurSolrUtils.firstRowPositionFromCurrentPage(turSEParameters));

        filterQueryRequest(turSEParameters, query);
        try {
            TurSEResults turSEResults = new TurSEResults();
            QueryResponse queryResponse = turSolrInstance.getSolrClient().query(query);
            turSEResultsParameters(turSEParameters, query, turSEResults, queryResponse);
            turSEResults.setSpellCheck(spellCheckTerm(turSolrInstance, turSEParameters.getQuery()));
            turSEResults.setResults(queryResponse.getResults()
                    .stream().map(TurSolrUtils::createTurSEResultFromDocument).collect(Collectors.toList()));

            return turSEResults;
        } catch (IOException | SolrServerException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    private static void filterQueryRequest(TurSEParameters turSEParameters, SolrQuery query) {
        if (turSEParameters.getFilterQueries() != null) {
            String[] filterQueryArr = new String[turSEParameters.getFilterQueries().size()];
            filterQueryArr = turSEParameters.getFilterQueries().toArray(filterQueryArr);
            query.setFilterQueries(filterQueryArr);
        }
    }

    private List<TurSNSiteFieldExt> prepareQueryHL(TurSNSite turSNSite, SolrQuery query) {
        List<TurSNSiteFieldExt> turSNSiteHlFieldExts = getHLFields(turSNSite);
        if (isHL(turSNSite, turSNSiteHlFieldExts)) {
            StringBuilder hlFields = new StringBuilder();
            turSNSiteHlFieldExts.forEach(turSNSiteHlFieldExt -> {
                if (!hlFields.isEmpty()) {
                    hlFields.append(",");
                }
                hlFields.append(turSNSiteHlFieldExt.getName());
            });
            query.setHighlight(true).setHighlightSnippets(1);
            query.setParam("hl.fl", hlFields.toString());
            query.setParam("hl.fragsize", "0");
            query.setParam("hl.simple.pre", turSNSite.getHlPre());
            query.setParam("hl.simple.post", turSNSite.getHlPost());

        }
        return turSNSiteHlFieldExts;
    }

    private List<TurSNSiteFieldExt> getHLFields(TurSNSite turSNSite) {
        return turSNSiteFieldExtRepository.findByTurSNSiteAndHlAndEnabled(turSNSite, 1, 1);
    }

    private Map<String, List<String>> getHL(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteHlFieldExts,
                                            QueryResponse queryResponse, SolrDocument document) {
        return isHL(turSNSite, turSNSiteHlFieldExts) ?
                queryResponse.getHighlighting().get(document.get("id").toString()) : null;
    }

    private static boolean isHL(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteHlFieldExts) {
        return turSNSite.getHl() == 1 && turSNSiteHlFieldExts != null && !turSNSiteHlFieldExts.isEmpty();
    }

    private Map<String, TurSNSiteFieldExt> getFieldExtMap(TurSNSite turSNSite) {
        return turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite,
                1).stream().collect(Collectors
                .toMap(TurSNSiteFieldExt::getName, turSNSiteFieldExt -> turSNSiteFieldExt, (a, b) -> b));
    }

    private Map<String, Object> getRequiredFields(TurSNSite turSNSite) {
        return turSNSiteFieldExtRepository
                .findByTurSNSiteAndRequiredAndEnabled(turSNSite, 1, 1).stream()
                .collect(Collectors.toMap(TurSNSiteFieldExt::getName, TurSNSiteFieldExt::getDefaultValue, (a, b) -> b));
    }

    private TurSEResult createTurSEResult(Map<String, TurSNSiteFieldExt> fieldExtMap,
                                          Map<String, Object> requiredFields,
                                          SolrDocument document, Map<String, List<String>> hl) {
        addRequiredFieldsToDocument(requiredFields, document);
        return createTurSEResultFromDocument(fieldExtMap, document, hl);
    }

    @SuppressWarnings("unchecked")
    private TurSEResult createTurSEResultFromDocument(Map<String, TurSNSiteFieldExt> fieldExtMap, SolrDocument document,
                                                      Map<String, List<String>> hl) {
        Map<String, Object> fields = new HashMap<>();
        for (String attribute : document.getFieldNames()) {
            Object attrValue = document.getFieldValue(attribute);

            if (isHLAttribute(fieldExtMap, hl, attribute)) {
                attrValue = hl.get(attribute).get(0);
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
        TurSEResult turSEResult = new TurSEResult();
        turSEResult.setFields(fields);
        return turSEResult;
    }

    private static boolean isHLAttribute(Map<String, TurSNSiteFieldExt> fieldExtMap, Map<String,
            List<String>> hl, String attribute) {
        return fieldExtMap.containsKey(attribute) &&
                fieldExtMap.get(attribute).getType() == TurSEFieldType.STRING &&
                hl != null && hl.containsKey(attribute);
    }

    private void addRequiredFieldsToDocument(Map<String, Object> requiredFields, SolrDocument document) {
        for (Object requiredFieldObject : requiredFields.keySet().toArray()) {
            String requiredField = (String) requiredFieldObject;
            if (!document.containsKey(requiredField)) {
                document.addField(requiredField, requiredFields.get(requiredField));
            }
        }
    }
}
