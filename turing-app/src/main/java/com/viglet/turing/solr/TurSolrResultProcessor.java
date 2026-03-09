package com.viglet.turing.solr;

import static com.viglet.turing.solr.TurSolrConstants.ID;
import static com.viglet.turing.solr.TurSolrConstants.MORE_LIKE_THIS;
import static com.viglet.turing.solr.TurSolrConstants.TITLE;
import static com.viglet.turing.solr.TurSolrConstants.TYPE;
import static com.viglet.turing.solr.TurSolrConstants.URL;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.Group;
import org.apache.solr.client.solrj.response.GroupCommand;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.RangeFacet;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.util.SimpleOrderedMap;
import org.springframework.util.CollectionUtils;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.facet.TurSEFacetResultAttr;
import com.viglet.turing.se.result.TurSEGenericResults;
import com.viglet.turing.se.result.TurSEGroup;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.TurSNFieldProcess;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.sn.facet.TurSNCustomFacetDefinition;
import com.viglet.turing.sn.facet.TurSNFacetDefinition;

public class TurSolrResultProcessor {

        private final TurSNFieldProcess turSNFieldProcess;
        private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;

        public TurSolrResultProcessor(TurSNFieldProcess turSNFieldProcess,
                        TurSNSiteFieldExtRepository turSNSiteFieldExtRepository) {
                this.turSNFieldProcess = turSNFieldProcess;
                this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        }

        public TurSEResults getResults(TurSolrInstance turSolrInstance, TurSNSite turSNSite,
                        SolrQuery query, TurSolrQueryContext queryContext,
                        QueryResponse queryResponse,
                        TurSolrQueryBuilder turSolrQueryBuilder) {
                TurSEResults turSEResults = TurSEResults.builder().build();
                turSEResultsParameters(queryContext.getTurSEParameters(), query, turSEResults, queryResponse);
                processSEResultsFacet(turSNSite, turSEResults, queryResponse, queryContext.getFacetFieldExtList());
                processResults(turSNSite, queryContext, turSEResults,
                                queryResponse);
                processGroups(query, turSolrInstance, turSNSite, queryContext, turSEResults, queryResponse,
                                turSolrQueryBuilder);
                turSEResults.setSpellCheck(queryContext.getSpellCheckResult());
                return turSEResults;
        }

        private void processResults(TurSNSite turSNSite,
                        TurSolrQueryContext queryContext, TurSEResults turSEResults,
                        QueryResponse queryResponse) {
                List<TurSESimilarResult> similarResults = new ArrayList<>();
                turSEResults.setResults(addSolrDocumentsToSEResults(queryResponse.getResults(), turSNSite,
                                queryContext, queryResponse, similarResults));
                setMLT(turSNSite, queryContext.getMltFieldExtList(), turSEResults, similarResults);
        }

        private void processGroups(SolrQuery query, TurSolrInstance turSolrInstance,
                        TurSNSite turSNSite, TurSolrQueryContext queryContext, TurSEResults turSEResults,
                        QueryResponse queryResponse, TurSolrQueryBuilder turSolrQueryBuilder) {
                if (!turSolrQueryBuilder.hasGroup(queryContext.getTurSEParameters())
                                || queryResponse.getGroupResponse() == null) {
                        return;
                }

                List<TurSEGroup> turSEGroups = new ArrayList<>();
                populateGroupsFromResponse(queryResponse, turSNSite, queryContext, turSEGroups);

                if (shouldApplyWildcardQuery(turSNSite, query)) {
                        enrichGroupsWithWildcardResults(query, turSolrInstance, turSNSite,
                                        queryContext, queryResponse, turSEGroups);
                }

                turSEResults.setGroups(turSEGroups);
        }

        private void populateGroupsFromResponse(QueryResponse queryResponse, TurSNSite turSNSite,
                        TurSolrQueryContext queryContext, List<TurSEGroup> turSEGroups) {
                var groupResponse = queryResponse.getGroupResponse();
                if (groupResponse == null || CollectionUtils.isEmpty(groupResponse.getValues())) {
                        return;
                }

                for (GroupCommand groupCommand : groupResponse.getValues()) {
                        addGroupsFromCommand(turSNSite, queryContext, queryResponse, turSEGroups, groupCommand);
                }
        }

        private void addGroupsFromCommand(TurSNSite turSNSite, TurSolrQueryContext queryContext,
                        QueryResponse queryResponse, List<TurSEGroup> turSEGroups, GroupCommand groupCommand) {
                if (CollectionUtils.isEmpty(groupCommand.getValues())) {
                        return;
                }

                for (Group group : groupCommand.getValues()) {
                        addGroupIfPresent(turSNSite, queryContext, queryResponse, turSEGroups, group);
                }
        }

        private void addGroupIfPresent(TurSNSite turSNSite, TurSolrQueryContext queryContext,
                        QueryResponse queryResponse, List<TurSEGroup> turSEGroups, Group group) {
                if (group.getGroupValue() == null) {
                        return;
                }

                turSEGroups.add(setTurSEGroup(turSNSite, queryContext, queryResponse, group));
        }

        private boolean shouldApplyWildcardQuery(TurSNSite turSNSite, SolrQuery query) {
                return TurSolr.enabledWildcardNoResults(turSNSite) && TurSolr.isNotQueryExpression(query);
        }

        private void enrichGroupsWithWildcardResults(SolrQuery query, TurSolrInstance turSolrInstance,
                        TurSNSite turSNSite, TurSolrQueryContext queryContext,
                        QueryResponse queryResponse, List<TurSEGroup> turSEGroups) {
                SolrQuery wildcardQuery = query.getCopy();
                TurSolr.addAWildcardInQuery(wildcardQuery);
                TurSolr.executeSolrQuery(turSolrInstance, wildcardQuery)
                                .ifPresent(wildcardResponse -> processWildcardGroupResults(
                                                wildcardResponse, turSNSite, queryContext,
                                                queryResponse, turSEGroups));
        }

        private void processWildcardGroupResults(QueryResponse wildcardResponse, TurSNSite turSNSite,
                        TurSolrQueryContext queryContext, QueryResponse originalResponse,
                        List<TurSEGroup> turSEGroups) {
                wildcardResponse.getGroupResponse().getValues()
                                .forEach(groupCommand -> groupCommand.getValues()
                                                .forEach(group -> updateOrAddGroup(group, turSNSite,
                                                                queryContext, originalResponse,
                                                                turSEGroups)));
        }

        private void updateOrAddGroup(Group group, TurSNSite turSNSite,
                        TurSolrQueryContext queryContext, QueryResponse queryResponse,
                        List<TurSEGroup> turSEGroups) {
                Optional<TurSEGroup> existingGroup = seGroupsHasGroup(turSEGroups, group);

                if (existingGroup.isPresent()) {
                        updateGroupWithWildcardResults(existingGroup.get(), group, turSNSite,
                                        queryContext, queryResponse);
                } else {
                        addNewGroupFromWildcard(group, turSNSite, queryContext, queryResponse,
                                        turSEGroups);
                }
        }

        private void updateGroupWithWildcardResults(TurSEGroup turSEGroup, Group group,
                        TurSNSite turSNSite, TurSolrQueryContext queryContext,
                        QueryResponse queryResponse) {
                if (turSEGroup.getResults().isEmpty() && !group.getResult().isEmpty()) {
                        turSEGroup.setResults(addSolrDocumentsToSEResults(
                                        group.getResult(), turSNSite, queryContext, queryResponse, null));
                }
        }

        private void addNewGroupFromWildcard(Group group, TurSNSite turSNSite,
                        TurSolrQueryContext queryContext, QueryResponse queryResponse,
                        List<TurSEGroup> turSEGroups) {
                Optional.ofNullable(group.getGroupValue())
                                .ifPresent(g -> turSEGroups.add(setTurSEGroup(turSNSite,
                                                queryContext, queryResponse, group)));
        }

        private static Optional<TurSEGroup> seGroupsHasGroup(List<TurSEGroup> turSEGroups,
                        Group group) {
                return turSEGroups.stream().filter(o -> o.getName() != null && group.getGroupValue() != null
                                && o.getName().equals(group.getGroupValue())).findFirst();
        }

        private TurSEGroup setTurSEGroup(TurSNSite turSNSite, TurSolrQueryContext queryContext,
                        QueryResponse queryResponse, Group group) {
                return TurSEGroup.builder().name(group.getGroupValue())
                                .numFound(group.getResult().getNumFound())
                                .currentPage(queryContext.getTurSEParameters().getCurrentPage())
                                .limit(queryContext.getTurSEParameters().getRows())
                                .pageCount(getNumberOfPages(group.getResult().getNumFound(),
                                                queryContext.getTurSEParameters().getRows()))
                                .results(addSolrDocumentsToSEResults(group.getResult(), turSNSite,
                                                queryContext, queryResponse, null))
                                .build();
        }

        private void setMLT(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList,
                        TurSEResults turSEResults, List<TurSESimilarResult> similarResults) {
                if (hasMLT(turSNSite, turSNSiteMLTFieldExtList))
                        turSEResults.setSimilarResults(similarResults);
        }

        public void turSEResultsParameters(TurSEParameters turSEParameters, SolrQuery query,
                        TurSEResults turSEResults, QueryResponse queryResponse) {
                if (queryResponse.getResults() != null) {
                        turSEResults.setNumFound(queryResponse.getResults().getNumFound());
                        turSEResults.setStart(queryResponse.getResults().getStart());
                } else if (queryResponse.getGroupResponse() != null) {
                        turSEResults.setNumFound(queryResponse.getGroupResponse().getValues().stream()
                                        .mapToInt(GroupCommand::getMatches).sum());
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
                return TurSNUtils.isTrue(turSNSite.getMlt())
                                && !CollectionUtils.isEmpty(turSNSiteMLTFieldExtList);
        }

        private List<TurSEResult> addSolrDocumentsToSEResults(SolrDocumentList solrDocumentList,
                        TurSNSite turSNSite, TurSolrQueryContext queryContext,
                        QueryResponse queryResponse, List<TurSESimilarResult> similarResults) {
                List<TurSEResult> results = new ArrayList<>();
                Optional.ofNullable(solrDocumentList).ifPresent(documents -> documents.forEach(document -> {
                        processSEResultsMLT(turSNSite, queryContext.getMltFieldExtList(), similarResults, document,
                                        queryResponse);
                        results.add(createTurSEResult(getFieldExtMap(turSNSite), getRequiredFields(turSNSite),
                                        document,
                                        getHL(turSNSite, queryContext.getHlFieldExtList(), queryResponse, document)));
                }));
                return results;
        }

        private void processSEResultsMLT(TurSNSite turSNSite,
                        List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList,
                        List<TurSESimilarResult> similarResults, SolrDocument document,
                        QueryResponse queryResponse) {
                if (TurSNUtils.isTrue(turSNSite.getMlt()) && !turSNSiteMLTFieldExtList.isEmpty()) {
                        @SuppressWarnings("rawtypes")
                        SimpleOrderedMap mltResp = (SimpleOrderedMap) queryResponse.getResponse().get(MORE_LIKE_THIS);
                        ((SolrDocumentList) mltResp.get((String) document.get(ID)))
                                        .forEach(mltDocument -> similarResults.add(TurSESimilarResult.builder()
                                                        .id(TurSolrField.convertFieldToString(
                                                                        mltDocument.getFieldValue(ID)))
                                                        .title(TurSolrField
                                                                        .convertFieldToString(mltDocument
                                                                                        .getFieldValue(TITLE)))
                                                        .type(TurSolrField
                                                                        .convertFieldToString(mltDocument
                                                                                        .getFieldValue(TYPE)))
                                                        .url(TurSolrField.convertFieldToString(
                                                                        mltDocument.getFieldValue(URL)))
                                                        .build()));
                }
        }

        private void processSEResultsFacet(TurSNSite turSNSite, TurSEResults turSEResults,
                        QueryResponse queryResponse, List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList) {
                if (wasFacetConfigured(turSNSite, turSNSiteFacetFieldExtList)) {
                        List<TurSEFacetResult> facetQueryResults = setFacetQueries(turSNSite,
                                        queryResponse);
                        List<TurSEFacetResult> facetRangeResults = setFacetRanges(queryResponse);
                        List<TurSEFacetResult> facetResults = new ArrayList<>(facetQueryResults);
                        facetResults.addAll(facetRangeResults);
                        facetResults.addAll(setFacetFields(queryResponse, facetRangeResults));
                        facetResults.forEach(facet -> {
                                turSNFieldProcess.getTurSNSiteFieldOrdering(turSNSite.getId())
                                                .ifPresent(fields -> fields.forEach(fieldExtension -> facetResults
                                                                .stream()
                                                                .filter(facetResult -> fieldExtension
                                                                                .getFacetPosition() != null
                                                                                && facetResult.getFacet().equals(
                                                                                                fieldExtension.getName()))
                                                                .findFirst().ifPresent(facetResult -> facetResult
                                                                                .setFacetPosition(fieldExtension
                                                                                                .getFacetPosition()))));
                                turSEResults.setFacetResults(facetResults.stream()
                                                .sorted(Comparator.comparing(TurSEFacetResult::getFacetPosition))
                                                .toList());
                        });
                }
        }

        private List<TurSEFacetResult> setFacetQueries(TurSNSite turSNSite,
                        QueryResponse queryResponse) {
                Map<String, Integer> facetQueryValues = Optional.ofNullable(queryResponse.getFacetQuery())
                                .orElse(Collections.emptyMap());
                if (facetQueryValues.isEmpty()) {
                        return Collections.emptyList();
                }

                Map<String, Map<String, Integer>> countsByFacetName = new LinkedHashMap<>();
                facetQueryValues.forEach((key, count) -> {
                        String[] keyValue = key.split(Pattern.quote(
                                        TurSolrQueryBuilder.CUSTOM_FACET_QUERY_SEPARATOR),
                                        2);
                        if (keyValue.length == 2) {
                                countsByFacetName
                                                .computeIfAbsent(keyValue[0], f -> new LinkedHashMap<>())
                                                .put(keyValue[1], count);
                        }
                });

                List<TurSEFacetResult> facetResults = new ArrayList<>();
                Set<String> processedFacetNames = new HashSet<>();
                List<TurSNFacetDefinition> customFacetDefinitions = turSNSiteFieldExtRepository
                                .findByTurSNSiteAndEnabled(turSNSite, 1).stream()
                                .flatMap(field -> Optional.ofNullable(field.getCustomFacets())
                                                .orElse(Collections.emptySet()).stream()
                                                .map(customFacet -> (TurSNFacetDefinition) new TurSNCustomFacetDefinition(
                                                                field, customFacet, null)))
                                .sorted(Comparator.comparing(TurSNFacetDefinition::getPosition)
                                                .thenComparing(TurSNFacetDefinition::getName,
                                                                Comparator.nullsLast(String::compareToIgnoreCase)))
                                .toList();

                customFacetDefinitions.forEach(customFacet -> {
                        Map<String, Integer> itemCounts = countsByFacetName
                                        .get(customFacet.getName());
                        if (itemCounts != null) {
                                TurSEFacetResult result = new TurSEFacetResult();
                                result.setFacet(customFacet.getName());
                                customFacet.getItems().stream()
                                                .sorted(Comparator
                                                                .comparing(this::getCustomFacetItemPosition)
                                                                .thenComparing(TurSNSiteCustomFacetItem::getLabel,
                                                                                Comparator
                                                                                                .nullsLast(String::compareToIgnoreCase)))
                                                .forEach(item -> {
                                                        Integer count = itemCounts
                                                                        .get(item.getLabel());
                                                        if (count != null) {
                                                                result.add(item.getLabel(),
                                                                                new TurSEFacetResultAttr(
                                                                                                item.getLabel(),
                                                                                                count));
                                                        }
                                                });
                                facetResults.add(result);
                                processedFacetNames.add(customFacet.getName());
                        }
                });

                countsByFacetName.forEach((facetName, itemCounts) -> {
                        if (!processedFacetNames.contains(facetName)) {
                                TurSEFacetResult result = new TurSEFacetResult();
                                result.setFacet(facetName);
                                itemCounts.entrySet().stream()
                                                .sorted(Map.Entry.comparingByKey())
                                                .forEach(item -> result.add(item.getKey(),
                                                                new TurSEFacetResultAttr(item.getKey(),
                                                                                item.getValue())));
                                facetResults.add(result);
                        }
                });

                return facetResults;
        }

        private Integer getCustomFacetItemPosition(TurSNSiteCustomFacetItem customFacetItem) {
                return Optional.ofNullable(customFacetItem.getPosition())
                                .filter(position -> position > 0)
                                .orElse(Integer.MAX_VALUE);
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

        private static List<TurSEFacetResult> setFacetFields(QueryResponse queryResponse,
                        List<TurSEFacetResult> facetRangeList) {
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

        private boolean wasFacetConfigured(TurSNSite turSNSite,
                        List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList) {
                return TurSNUtils.isTrue(turSNSite.getFacet()) && turSNSite.getItemsPerFacet() != null
                                && !CollectionUtils.isEmpty(turSNSiteFacetFieldExtList);
        }

        public TurSEResult createTurSEResult(Map<String, TurSNSiteFieldExt> fieldExtMap,
                        Map<String, Object> requiredFields, SolrDocument document,
                        Map<String, List<String>> hl) {
                addRequiredFieldsToDocument(requiredFields, document);
                return createTurSEResultFromDocument(fieldExtMap, document, hl);
        }

        private TurSEResult createTurSEResultFromDocument(Map<String, TurSNSiteFieldExt> fieldExtMap,
                        SolrDocument document, Map<String, List<String>> hl) {
                Map<String, Object> fields = new HashMap<>();
                for (String attribute : document.getFieldNames()) {
                        Object attrValue = document.getFieldValue(attribute);
                        if (isHLAttribute(fieldExtMap, hl, attribute)) {
                                attrValue = hl.get(attribute).getFirst();
                        }
                        if (attribute != null && fields.containsKey(attribute)) {
                                Object existingValue = fields.get(attribute);
                                List<Object> attributeValues;
                                if (existingValue instanceof List<?>) {

                                        attributeValues = new ArrayList<>((List<?>) existingValue);
                                } else {
                                        attributeValues = new ArrayList<>();
                                        attributeValues.add(existingValue);
                                }
                                attributeValues.add(attrValue);
                                fields.put(attribute, attributeValues);
                        } else {
                                fields.put(attribute, attrValue);
                        }
                }
                return TurSEResult.builder().fields(fields).build();
        }

        private static boolean isHLAttribute(Map<String, TurSNSiteFieldExt> fieldExtMap,
                        Map<String, List<String>> hl, String attribute) {
                return fieldExtMap.containsKey(attribute)
                                && (Collections
                                                .unmodifiableSet(EnumSet.of(TurSEFieldType.TEXT, TurSEFieldType.STRING))
                                                .contains(fieldExtMap.get(attribute).getType()))
                                && hl != null && hl.containsKey(attribute);
        }

        private void addRequiredFieldsToDocument(Map<String, Object> requiredFields,
                        SolrDocument document) {
                Arrays.stream(requiredFields.keySet().toArray()).map(String.class::cast)
                                .filter(requiredField -> !document.containsKey(requiredField))
                                .forEach(requiredField -> document.addField(requiredField,
                                                requiredFields.get(requiredField)));
        }

        public Map<String, TurSNSiteFieldExt> getFieldExtMap(TurSNSite turSNSite) {
                return turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite, 1).stream()
                                .collect(Collectors.toMap(TurSNSiteFieldExt::getName,
                                                turSNSiteFieldExt -> turSNSiteFieldExt, (a, b) -> b));
        }

        public Map<String, Object> getRequiredFields(TurSNSite turSNSite) {
                return turSNSiteFieldExtRepository.findByTurSNSiteAndRequiredAndEnabled(turSNSite, 1, 1)
                                .stream().filter(Objects::nonNull)
                                .collect(Collectors.toMap(TurSNSiteFieldExt::getName,
                                                TurSNSiteFieldExt::getDefaultValue, (a, b) -> b));
        }

        public Map<String, List<String>> getHL(TurSNSite turSNSite,
                        List<TurSNSiteFieldExt> turSNSiteHlFieldExtList, QueryResponse queryResponse,
                        SolrDocument document) {
                return isHL(turSNSite, turSNSiteHlFieldExtList) && queryResponse.getHighlighting() != null
                                ? queryResponse.getHighlighting().get(document.get(ID).toString())
                                : null;
        }

        public static boolean isHL(TurSNSite turSNSite,
                        List<TurSNSiteFieldExt> turSNSiteHlFieldExtList) {
                return TurSNUtils.isTrue(turSNSite.getHl())
                                && !CollectionUtils.isEmpty(turSNSiteHlFieldExtList);
        }
}
