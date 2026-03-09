package com.viglet.turing.solr;

import static com.viglet.turing.solr.TurSolrConstants.AND;
import static com.viglet.turing.solr.TurSolrConstants.ASC;
import static com.viglet.turing.solr.TurSolrConstants.BOOST_QUERY;
import static com.viglet.turing.solr.TurSolrConstants.COUNT;
import static com.viglet.turing.solr.TurSolrConstants.DEF_TYPE;
import static com.viglet.turing.solr.TurSolrConstants.EDISMAX;
import static com.viglet.turing.solr.TurSolrConstants.EMPTY;
import static com.viglet.turing.solr.TurSolrConstants.FACET_OR;
import static com.viglet.turing.solr.TurSolrConstants.FILTER_QUERY_OR;
import static com.viglet.turing.solr.TurSolrConstants.HYPHEN;
import static com.viglet.turing.solr.TurSolrConstants.INDEX;
import static com.viglet.turing.solr.TurSolrConstants.NEWEST;
import static com.viglet.turing.solr.TurSolrConstants.NO_FACET_NAME;
import static com.viglet.turing.solr.TurSolrConstants.OLDEST;
import static com.viglet.turing.solr.TurSolrConstants.OR;
import static com.viglet.turing.solr.TurSolrConstants.OR_AND;
import static com.viglet.turing.solr.TurSolrConstants.OR_OR;
import static com.viglet.turing.solr.TurSolrConstants.PLUS_ONE;
import static com.viglet.turing.solr.TurSolrConstants.Q_OP;
import static com.viglet.turing.solr.TurSolrConstants.RECENT_DATES;
import static com.viglet.turing.solr.TurSolrConstants.ROWS;
import static com.viglet.turing.solr.TurSolrConstants.SOLR_DATE_PATTERN;
import static com.viglet.turing.solr.TurSolrConstants.TRUE;
import static com.viglet.turing.solr.TurSolrConstants.TURING_ENTITY;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.KeyValue;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.common.params.GroupParams;
import org.apache.solr.common.params.HighlightParams;
import org.apache.solr.common.params.MoreLikeThisParams;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.sn.bean.TurSNFilterParams;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetRangeEnum;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetSortEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacet;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteCustomFacetItem;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldSortEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.ranking.TurSNRankingExpression;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.sn.facet.TurSNCustomFacetDefinition;
import com.viglet.turing.sn.facet.TurSNFacetDefinition;
import com.viglet.turing.sn.facet.TurSNFacetMapForFilterQuery;
import com.viglet.turing.sn.facet.TurSNFacetProperties;
import com.viglet.turing.sn.facet.TurSNFacetTypeContext;
import com.viglet.turing.sn.tr.TurSNTargetingRuleMethod;
import com.viglet.turing.sn.tr.TurSNTargetingRules;
import com.viglet.turing.spring.utils.TurPersistenceUtils;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class TurSolrQueryBuilder {
    public static final String CUSTOM_FACET_QUERY_SEPARATOR = "::";
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNRankingExpressionRepository turSNRankingExpressionRepository;
    private final TurSNRankingConditionRepository turSNRankingConditionRepository;
    private final TurSNTargetingRules turSNTargetingRules;

    public TurSolrQueryBuilder(TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNRankingExpressionRepository turSNRankingExpressionRepository,
            TurSNRankingConditionRepository turSNRankingConditionRepository,
            TurSNTargetingRules turSNTargetingRules) {
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNRankingExpressionRepository = turSNRankingExpressionRepository;
        this.turSNRankingConditionRepository = turSNRankingConditionRepository;
        this.turSNTargetingRules = turSNTargetingRules;
    }

    @NotNull
    public SolrQuery prepareSolrQuery(TurSNSiteSearchContext context, TurSNSite turSNSite,
            TurSEParameters turSEParameters, TurSESpellCheckResult turSESpellCheckResult) {
        SolrQuery query = new SolrQuery();
        query.set(DEF_TYPE, EDISMAX);
        query.set(Q_OP, AND);
        setRows(turSNSite, turSEParameters);
        setSortEntry(turSNSite, query, turSEParameters);

        if (usesExactMatch(turSNSite, turSEParameters)) {
            turSEParameters.setQuery(
                    String.format("%s:%s", turSNSite.getExactMatchField(), turSEParameters.getQuery()));
        }

        if (TurSNUtils.isAutoCorrectionEnabled(context, turSNSite)) {
            query.setQuery(TurSNUtils.hasCorrectedText(turSESpellCheckResult)
                    ? turSESpellCheckResult.getCorrectedText()
                    : turSEParameters.getQuery());
        } else {
            query.setQuery(turSEParameters.getQuery());
        }
        if (!hasGroup(turSEParameters)) {
            query.setRows(turSEParameters.getRows())
                    .setStart(TurSolrUtils.firstRowPositionFromCurrentPage(turSEParameters));
        }
        prepareQueryFilterQuery(turSEParameters.getTurSNFilterParams(), query, turSNSite);
        prepareQueryTargetingRules(context.getTurSNSitePostParamsBean(), query);
        if (hasGroup(turSEParameters)) {
            prepareGroup(turSEParameters, query);
        }
        prepareBoostQuery(turSNSite, query);
        return query;
    }

    private static boolean usesExactMatch(TurSNSite turSNSite, TurSEParameters turSEParameters) {
        return turSEParameters.getQuery().trim().startsWith("\"")
                && turSEParameters.getQuery().trim().endsWith("\"")
                && StringUtils.hasText(turSNSite.getExactMatchField())
                && turSNSite.getExactMatch() != null && turSNSite.getExactMatch().equals(1);
    }

    private void prepareBoostQuery(TurSNSite turSNSite, SolrQuery query) {
        List<TurSNSiteFieldExt> turSNSiteFieldExtList = turSNSiteFieldExtRepository
                .findByTurSNSite(TurPersistenceUtils.orderByNameIgnoreCase(), turSNSite);
        Set<TurSNRankingExpression> turSNRankingExpression = turSNRankingExpressionRepository
                .findByTurSNSite(TurPersistenceUtils.orderByNameIgnoreCase(), turSNSite);
        if (hasRankingConditions(turSNRankingExpression)) {
            String[] expressionString = turSNRankingExpression.stream()
                    .map(expression -> String.format(Locale.US, "%s^%.1f",
                            "(" + boostQueryAttributes(expression, turSNSiteFieldExtList) + ")",
                            expression.getWeight()))
                    .toArray(String[]::new);
            query.set(BOOST_QUERY, expressionString);
        }
    }

    private boolean hasRankingConditions(Set<TurSNRankingExpression> turSNRankingExpressions) {
        return turSNRankingExpressions.stream()
                .anyMatch(expressions -> !turSNRankingConditionRepository
                        .findByTurSNRankingExpression(expressions).isEmpty());
    }

    private String boostQueryAttributes(TurSNRankingExpression expression,
            List<TurSNSiteFieldExt> turSNSiteFieldExtList) {
        return turSNRankingConditionRepository.findByTurSNRankingExpression(expression).stream()
                .map(condition -> {
                    TurSNSiteFieldExt turSNSiteFieldExt = turSNSiteFieldExtList.stream()
                            .filter(field -> field.getName().equals(condition.getAttribute()))
                            .findFirst().orElse(TurSNSiteFieldExt.builder().build());
                    if (turSNSiteFieldExt.getType() != null
                            && turSNSiteFieldExt.getType().equals(TurSEFieldType.DATE)
                            && condition.getValue().equalsIgnoreCase(ASC)) {
                        return String.format("_query_:\"" + RECENT_DATES + "\"",
                                condition.getAttribute());
                    }
                    return String.format("%s:\"%s\"", condition.getAttribute(),
                            condition.getValue());
                }).collect(Collectors.joining(betweenSpaces(AND)));
    }

    private void prepareGroup(TurSEParameters turSEParameters, SolrQuery query) {
        query.set(GroupParams.GROUP, TRUE).set(GroupParams.GROUP_FIELD, turSEParameters.getGroup())
                .set(GroupParams.GROUP_LIMIT, turSEParameters.getRows())
                .set(GroupParams.GROUP_OFFSET,
                        TurSolrUtils.firstRowPositionFromCurrentPage(turSEParameters))
                .set(ROWS, 100);

    }

    public boolean hasGroup(TurSEParameters turSEParameters) {
        return StringUtils.hasText(turSEParameters.getGroup());
    }

    private void prepareQueryFilterQuery(TurSNFilterParams turSNFilterParams, SolrQuery query,
            TurSNSite turSNSite) {
        Optional.of(getFilterQueryMap(turSNFilterParams, turSNSite))
                .filter(facetMapForFilterQuery -> !CollectionUtils.isEmpty(facetMapForFilterQuery))
                .ifPresent(facetMapForFilterQuery -> query.addFilterQuery(String.format("%s(%s)",
                        getFacetTypeConditionInFilterQuery(
                                new TurSNFacetTypeContext(turSNSite, turSNFilterParams)),
                        setFilterQueryString(
                                setFilterQueryMapModified(turSNSite, facetMapForFilterQuery)))));
    }

    @NotNull
    private TurSNFacetMapForFilterQuery setFilterQueryMapModified(TurSNSite turSNSite,
            TurSNFacetMapForFilterQuery facetMapForFilterQuery) {
        TurSNFacetMapForFilterQuery filterQueryMapModified = new TurSNFacetMapForFilterQuery();
        getFilterQueryMap(facetMapForFilterQuery, turSNSite).forEach((facetName, properties) -> {
            if (filterQueryMapModified.containsKey(facetName)) {
                filterQueryMapModified.get(facetName).getFacetItems()
                        .addAll(getFilterQueryValue(properties.getFacetItems()));
            } else {
                filterQueryMapModified.put(facetName,
                        TurSNFacetProperties.builder().facetType(properties.getFacetType())
                                .facetItemType(properties.getFacetItemType())
                                .facetItems(getFilterQueryValue(properties.getFacetItems()))
                                .build());
            }
        });
        return filterQueryMapModified;
    }

    @NotNull
    private static StringBuilder setFilterQueryString(
            TurSNFacetMapForFilterQuery facetMapForFilterQuery) {
        List<String> facetsAnd = new ArrayList<>();
        List<String> facetsOr = new ArrayList<>();
        StringBuilder filterQueryString = new StringBuilder();
        facetMapForFilterQuery.forEach((f, properties) -> {
            StringBuilder filterQueryStringItems = new StringBuilder();
            if (properties.getFacetItemType().equals(TurSNSiteFacetFieldEnum.OR)) {
                filterQueryStringItems.append(String.format("(%s)",
                        String.join(betweenSpaces(OR), properties.getFacetItems())));
            } else {
                filterQueryStringItems.append(String.format("(%s)",
                        String.join(betweenSpaces(AND), properties.getFacetItems())));
            }
            if (properties.getFacetType().equals(TurSNSiteFacetFieldEnum.OR)) {
                facetsOr.add(filterQueryStringItems.toString());
            } else {
                facetsAnd.add(filterQueryStringItems.toString());
            }
        });
        if (!facetsAnd.isEmpty()) {
            filterQueryString.append(String.join(betweenSpaces(AND), facetsAnd));
            if (!facetsOr.isEmpty()) {
                filterQueryString.append(betweenSpaces(OR));
            }
        }
        if (!facetsOr.isEmpty()) {
            filterQueryString.append(String.join(betweenSpaces(OR), facetsOr));
        }
        return filterQueryString;
    }

    private static String betweenSpaces(String operator) {
        return " %s ".formatted(operator);
    }

    @NotNull
    private static List<String> getFilterQueryValue(List<String> value) {
        return value.stream()
                .map(fq -> queryWithoutExpression(fq) ? addDoubleQuotesToValue(fq) : fq).toList();
    }

    private TurSNFacetMapForFilterQuery getFilterQueryMap(TurSNFilterParams turSNFilterParams,
            TurSNSite turSNSite) {
        TurSNFacetMapForFilterQuery facetMapForFilterQuery = new TurSNFacetMapForFilterQuery();
        List<TurSNSiteFieldExt> enabledFields = getEnabledFields(turSNSite);
        Optional.ofNullable(turSNFilterParams).ifPresent(filterQueryParameters -> {
            Optional.ofNullable(filterQueryParameters.getDefaultValues())
                    .ifPresent(f -> f.forEach(fItem -> addEnabledFieldAsFacetItem(
                            TurSNSiteFacetFieldEnum.DEFAULT, fItem, enabledFields,
                            facetMapForFilterQuery, turSNSite, turSNFilterParams)));
            Optional.ofNullable(filterQueryParameters.getAnd())
                    .ifPresent(f -> f.forEach(fItem -> addEnabledFieldAsFacetItem(
                            TurSNSiteFacetFieldEnum.AND, fItem, enabledFields,
                            facetMapForFilterQuery, turSNSite, turSNFilterParams)));
            Optional.ofNullable(filterQueryParameters.getOr()).ifPresent(f -> f
                    .forEach(fItem -> addEnabledFieldAsFacetItem(TurSNSiteFacetFieldEnum.OR, fItem,
                            enabledFields, facetMapForFilterQuery, turSNSite, turSNFilterParams)));
        });
        return facetMapForFilterQuery;
    }

    private static void addEnabledFieldAsFacetItem(TurSNSiteFacetFieldEnum facetType, String fq,
            List<TurSNSiteFieldExt> enabledFields,
            TurSNFacetMapForFilterQuery facetMapForFilterQuery, TurSNSite turSNSite,
            TurSNFilterParams turSNFilterParams) {

        Optional<CustomFacetFilterMapping> customFacetFilterMapping = getCustomFacetFilterMapping(fq,
                enabledFields);
        if (customFacetFilterMapping.isPresent()) {
            CustomFacetFilterMapping mapping = customFacetFilterMapping.get();
            TurSNFacetDefinition customFacetDefinition = new TurSNCustomFacetDefinition(
                    mapping.turSNSiteFieldExt(), mapping.turSNSiteCustomFacet(), null);
            TurSNFacetTypeContext context = new TurSNFacetTypeContext(
                    new TurSNSiteFieldExtDto(mapping.turSNSiteFieldExt()), turSNSite,
                    turSNFilterParams);
            TurSNSiteFacetFieldEnum currentFacetType = isFacetTypeDefault(facetType)
                    ? getFacetType(context, customFacetDefinition.getFacetType())
                    : facetType;
            addEnabledFacetItem(customFacetDefinition.getName(), currentFacetType,
                    getFacetItemType(context, customFacetDefinition.getFacetItemType()),
                    facetMapForFilterQuery,
                    setRangeFilterQuery(mapping.turSNSiteFieldExt().getName(),
                            mapping.turSNSiteFieldExt().getType(),
                            mapping.customFacetItem()));
            return;
        }

        TurCommonsUtils.getKeyValueFromColon(fq)
                .flatMap(kv -> enabledFields.stream()
                        .filter(facet -> facet.getName().equals(kv.getKey())).findFirst())
                .ifPresentOrElse(facet ->

                {
                    TurSNFacetTypeContext context = new TurSNFacetTypeContext(
                            new TurSNSiteFieldExtDto(facet), turSNSite, turSNFilterParams);
                    if (isFacetTypeDefault(facetType)) {
                        addEnabledFacetItem(facet.getName(), getFacetType(context),
                                getFacetItemType(context), facetMapForFilterQuery, fq);
                    } else {
                        addEnabledFacetItem(facet.getName(), facetType, getFacetItemType(context),
                                facetMapForFilterQuery, fq);
                    }
                }, () -> {
                    TurSNSiteFacetFieldEnum facetTypeValue = getFacetType(
                            new TurSNFacetTypeContext(turSNSite, turSNFilterParams));
                    addEnabledFacetItem(NO_FACET_NAME.concat(facetTypeValue.toString()),
                            facetTypeValue, getFacetItemTypeFromSite(turSNSite),
                            facetMapForFilterQuery, fq);
                });

    }

    private static Optional<CustomFacetFilterMapping> getCustomFacetFilterMapping(String fq,
            List<TurSNSiteFieldExt> enabledFields) {
        return TurCommonsUtils.getKeyValueFromColon(fq)
                .flatMap(kv -> enabledFields.stream()
                        .flatMap(field -> Optional.ofNullable(field.getCustomFacets())
                                .orElse(Collections.emptySet()).stream()
                                .filter(customFacet -> customFacet.getName().equals(kv.getKey()))
                                .flatMap(customFacet -> customFacet.getItems().stream()
                                        .filter(item -> item.getLabel().equals(kv.getValue()))
                                        .map(item -> new CustomFacetFilterMapping(
                                                customFacet.getName(), field, customFacet, item))))
                        .findFirst());
    }

    private static void addEnabledFacetItem(String facetName, TurSNSiteFacetFieldEnum facetType,
            TurSNSiteFacetFieldEnum facetItemType,
            TurSNFacetMapForFilterQuery facetMapForFilterQuery, String fq) {

        if (facetMapForFilterQuery.containsKey(facetName)) {
            facetMapForFilterQuery.get(facetName).getFacetItems().add(fq);
        } else {
            facetMapForFilterQuery.put(facetName,
                    TurSNFacetProperties.builder().facetType(facetType).facetItemType(facetItemType)
                            .facetItems(new ArrayList<>(Collections.singletonList(fq))).build());
        }
    }

    private static boolean isFacetTypeDefault(TurSNSiteFacetFieldEnum turSNSiteFacetFieldEnum) {
        return turSNSiteFacetFieldEnum == null
                || turSNSiteFacetFieldEnum.equals(TurSNSiteFacetFieldEnum.DEFAULT);
    }

    @NotNull
    private static TurSNSiteFacetFieldEnum getFaceTypeFromOperator(
            TurSNFilterQueryOperator operator) {
        return operator.equals(TurSNFilterQueryOperator.OR) ? TurSNSiteFacetFieldEnum.OR
                : TurSNSiteFacetFieldEnum.AND;
    }

    private static boolean operatorIsNotEmpty(TurSNFilterQueryOperator operator) {
        return operator != null && !operator.equals(TurSNFilterQueryOperator.NONE);
    }

    private static TurSNSiteFacetFieldEnum getFacetItemTypeFromSite(TurSNSite turSNSite) {
        return switch (turSNSite.getFacetItemType()) {
            case OR -> TurSNSiteFacetFieldEnum.OR;
            case null, default -> TurSNSiteFacetFieldEnum.AND;
        };
    }

    private static TurSNSiteFacetFieldEnum getFacetTypeFromSite(TurSNSite turSNSite) {
        return switch (turSNSite.getFacetType()) {
            case OR -> TurSNSiteFacetFieldEnum.OR;
            case null, default -> TurSNSiteFacetFieldEnum.AND;
        };
    }

    private TurSNFacetMapForFilterQuery getFilterQueryMap(
            TurSNFacetMapForFilterQuery facetMapForFilterQuery, TurSNSite turSNSite) {
        List<TurSNSiteFieldExt> dateFacet = turSNSiteFieldExtRepository
                .findByTurSNSiteAndFacetAndEnabledAndType(turSNSite, 1, 1, TurSEFieldType.DATE);
        if (!dateFacet.isEmpty()) {
            return getFilterQueryByDateRange(facetMapForFilterQuery, dateFacet);
        } else {
            return facetMapForFilterQuery;
        }

    }

    @NotNull
    private static TurSNFacetMapForFilterQuery getFilterQueryByDateRange(
            TurSNFacetMapForFilterQuery facetMapForFilterQuery, List<TurSNSiteFieldExt> dateFacet) {
        TurSNFacetMapForFilterQuery facetMapForFilterQueryFormatted = new TurSNFacetMapForFilterQuery();
        facetMapForFilterQuery.forEach((facetName, properties) -> {
            if (facetMapForFilterQueryFormatted.containsKey(facetName)) {
                facetMapForFilterQueryFormatted.get(facetName).getFacetItems()
                        .addAll(setFilterQueryRangeValue(properties.getFacetItems(), dateFacet));
            } else {
                facetMapForFilterQueryFormatted.put(facetName, TurSNFacetProperties.builder()
                        .facetType(properties.getFacetType())
                        .facetItemType(properties.getFacetItemType())
                        .facetItems(setFilterQueryRangeValue(properties.getFacetItems(), dateFacet))
                        .build());
            }
        });
        return facetMapForFilterQueryFormatted;
    }

    @NotNull
    private static List<String> setFilterQueryRangeValue(List<String> filterQueries,
            List<TurSNSiteFieldExt> dateFacet) {
        return filterQueries.stream().map(fq -> TurCommonsUtils.getKeyValueFromColon(fq)
                .map(facetKv -> dateFacet.stream()
                        .filter(dateFacetItem -> facetKv.getKey().equals(dateFacetItem.getName())
                                && isDateRangeFacet(dateFacetItem))
                        .findFirst().map(dateFacetItem -> setFilterQueryByRangeType(fq, facetKv,
                                dateFacetItem.getFacetRange()))
                        .orElse(fq))
                .orElse(fq)).toList();
    }

    private static String setFilterQueryByRangeType(String fq, KeyValue<String, String> facetKv,
            TurSNSiteFacetRangeEnum facetRange) {
        try {
            if (withoutExpression(facetKv.getValue())) {
                Date date = solrDateFormatter().parse(facetKv.getValue());
                return switch (facetRange) {
                    case DAY -> setFilterQueryRangeDay(date, facetKv);
                    case MONTH -> setFilterQueryRangeMonth(date, facetKv);
                    case YEAR -> setFilterQueryRangeYear(date, facetKv);
                    case DISABLED -> fq;
                };
            }
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
        return String.format("%s:[ %s TO %s ]", kv.getKey(), kv.getValue(),
                solrDateFormatter().format(endOfDay.getTime()));
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

    private static String getFacetTypeConditionInFilterQuery(TurSNFacetTypeContext context) {
        if (getFacetTypeAndFacetItemTypeValues(context).equals(OR_AND) || isOr(context)) {
            return FILTER_QUERY_OR;
        }
        return EMPTY;
    }

    private static TurSNSiteFacetFieldEnum getFacetItemType(TurSNFacetTypeContext context) {
        TurSNSiteFacetFieldEnum facetItemType = Optional.ofNullable(context.getTurSNSiteFacetFieldExtDto())
                .map(TurSNSiteFieldExtDto::getFacetItemType)
                .orElse(TurSNSiteFacetFieldEnum.DEFAULT);
        return getFacetItemType(context, facetItemType);
    }

    private static TurSNSiteFacetFieldEnum getFacetItemType(TurSNFacetTypeContext context,
            TurSNSiteFacetFieldEnum facetItemType) {
        TurSNFilterQueryOperator itemOperator = context.getTurSNFilterParams().getItemOperator();
        if (operatorIsNotEmpty(itemOperator)) {
            return getFaceTypeFromOperator(itemOperator);
        }
        if (context.isSpecificField() && !isFacetTypeDefault(facetItemType)) {
            return facetItemType;
        }
        return getFacetItemTypeFromSite(context.getTurSNSite());
    }

    private static TurSNSiteFacetFieldEnum getFacetType(TurSNFacetTypeContext context) {
        TurSNSiteFacetFieldEnum facetType = Optional.ofNullable(context.getTurSNSiteFacetFieldExtDto())
                .map(TurSNSiteFieldExtDto::getFacetType)
                .orElse(TurSNSiteFacetFieldEnum.DEFAULT);
        return getFacetType(context, facetType);
    }

    private static TurSNSiteFacetFieldEnum getFacetType(TurSNFacetTypeContext context,
            TurSNSiteFacetFieldEnum facetType) {
        TurSNFilterQueryOperator operator = context.getTurSNFilterParams().getOperator();
        if (operatorIsNotEmpty(operator)) {
            return getFaceTypeFromOperator(operator);
        }
        if (context.isSpecificField() && !isFacetTypeDefault(facetType)) {
            return facetType;
        }
        return getFacetTypeFromSite(context.getTurSNSite());
    }

    private static boolean isOr(TurSNFacetTypeContext context) {
        TurSNFilterQueryOperator operator = context.getTurSNFilterParams().getOperator();
        return (context.getTurSNSite().getFacetItemType().equals(TurSNSiteFacetFieldEnum.OR)
                && !operator.equals(TurSNFilterQueryOperator.AND))
                || operator.equals(TurSNFilterQueryOperator.OR);
    }

    @NotNull
    private static String addDoubleQuotesToValue(String q) {
        return TurCommonsUtils.getKeyValueFromColon(q)
                .map(kv -> String.format("%s:\"%s\"", kv.getKey(), kv.getValue()))
                .orElse(String.format("\"%s\"", q));
    }

    private static boolean queryWithoutExpression(String q) {
        String value = TurSolrUtils.getValueFromQuery(q);
        return !q.startsWith("(") && withoutExpression(value);

    }

    private static boolean withoutExpression(String value) {
        return !value.startsWith("[") && !value.startsWith("(") && !value.endsWith("*");

    }

    public List<TurSNSiteFieldExt> prepareQueryMLT(TurSNSite turSNSite, SolrQuery query) {
        List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList = turSNSiteFieldExtRepository
                .findByTurSNSiteAndMltAndEnabled(turSNSite, 1, 1);
        if (hasMLT(turSNSite, turSNSiteMLTFieldExtList)) {
            query.set(MoreLikeThisParams.MLT, true).set(MoreLikeThisParams.MATCH_INCLUDE, true)
                    .set(MoreLikeThisParams.MIN_DOC_FREQ, 1)
                    .set(MoreLikeThisParams.MIN_TERM_FREQ, 1)
                    .set(MoreLikeThisParams.MIN_WORD_LEN, 7).set(MoreLikeThisParams.BOOST, false)
                    .set(MoreLikeThisParams.MAX_QUERY_TERMS, 1000)
                    .set(MoreLikeThisParams.SIMILARITY_FIELDS,
                            String.join(",", turSNSiteMLTFieldExtList.stream()
                                    .map(TurSNSiteFieldExt::getName).toList()));
        }
        return turSNSiteMLTFieldExtList;
    }

    private String setFacetTypeConditionInFacet(TurSNFacetTypeContext context, String query) {
        return setFacetTypeConditionInFacet(context, query,
                context.getTurSNSiteFacetFieldExtDto().getName());
    }

    private String setFacetTypeConditionInFacet(TurSNFacetTypeContext context, String query,
            String filterKey) {
        List<String> fqFields = getFqFields(context.getTurSNFilterParams());
        switch (getFacetTypeAndFacetItemTypeValues(context)) {
            case OR_OR: {
                return FACET_OR.concat(query);
            }
            case OR_AND: {
                if (!fqFields.contains(filterKey)) {
                    return FACET_OR.concat(query);
                } else {
                    return query;
                }
            }
            default: {
                return query;
            }
        }
    }

    @NotNull
    public static String getFacetTypeAndFacetItemTypeValues(TurSNFacetTypeContext context) {
        return getFacetType(context).toString() + HYPHEN + getFacetItemType(context).toString();
    }

    public List<String> getFacetFieldsInFilterQuery(TurSNFacetTypeContext context) {
        List<TurSNSiteFieldExt> enabledFacets = getEnabledFacets(context.getTurSNSite());
        Set<String> enabledFacetNames = new HashSet<>(enabledFacets.stream()
                .map(TurSNSiteFieldExt::getName).toList());

        getEnabledFields(context.getTurSNSite()).stream()
                .flatMap(field -> Optional.ofNullable(field.getCustomFacets())
                        .orElse(Collections.emptySet()).stream())
                .map(TurSNSiteCustomFacet::getName)
                .filter(StringUtils::hasText)
                .forEach(enabledFacetNames::add);
        return getFqFields(context.getTurSNFilterParams()).stream()
                .filter(enabledFacetNames::contains).distinct().toList();
    }

    @NotNull
    public List<String> getFqFields(TurSNFilterParams turSNFilterParams) {
        List<String> fqFields = new ArrayList<>();
        fqFields.addAll(getFqFields(turSNFilterParams.getDefaultValues()));
        fqFields.addAll(getFqFields(turSNFilterParams.getAnd()));
        fqFields.addAll(getFqFields(turSNFilterParams.getOr()));
        return fqFields;
    }

    private static List<String> getFqFields(List<String> filterQueries) {
        return Optional
                .ofNullable(filterQueries).map(
                        fqOpt -> fqOpt.stream()
                                .map(fq -> TurCommonsUtils.getKeyValueFromColon(fq)
                                        .map(KeyValue::getKey).orElse(null))
                                .toList())
                .orElseGet(ArrayList::new);
    }

    public List<TurSNSiteFieldExt> prepareQueryFacet(TurSNSite turSNSite, SolrQuery query,
            TurSNFilterParams turSNFilterParams) {
        List<TurSNSiteFieldExt> enabledFacets = getEnabledFacets(turSNSite);
        return setFacetFields(turSNSite, query, turSNFilterParams, enabledFacets);
    }

    public List<TurSNSiteFieldExt> prepareQueryFacetWithOneFacet(TurSNSite turSNSite,
            SolrQuery query, TurSNFilterParams turSNFilterParams, String facetName) {
        List<TurSNSiteFieldExt> enabledFacets = getEnabledFields(turSNSite).stream()
                .filter(field -> field.getName().equals(facetName)
                        || Optional.ofNullable(field.getCustomFacets())
                                .orElse(Collections.emptySet()).stream()
                                .anyMatch(customFacet -> facetName.equals(customFacet.getName())))
                .toList();
        return setFacetFields(turSNSite, query, turSNFilterParams, enabledFacets);
    }

    private List<TurSNSiteFieldExt> setFacetFields(TurSNSite turSNSite, SolrQuery query,
            TurSNFilterParams turSNFilterParams, List<TurSNSiteFieldExt> enabledFacets) {
        if (wasFacetConfigured(turSNSite, enabledFacets)) {
            query.setFacet(true).setFacetLimit(turSNSite.getItemsPerFacet())
                    .setFacetSort(facetSortIsEmptyOrCount(turSNSite) ? COUNT : INDEX);
            enabledFacets.forEach(turSNSiteFacetFieldExt -> {
                TurSNFacetTypeContext context = new TurSNFacetTypeContext(
                        new TurSNSiteFieldExtDto(turSNSiteFacetFieldExt),
                        turSNSite, turSNFilterParams);
                setFacetSort(query, turSNSiteFacetFieldExt);
                if (isFacetEnabled(turSNSiteFacetFieldExt)) {
                    if (isDateRangeFacet(turSNSiteFacetFieldExt))
                        addFacetRange(context, query);
                    else
                        addFacetField(context, query);
                }

                if (hasCustomFacets(turSNSiteFacetFieldExt))
                    addCustomFacetQuery(context, query, turSNSiteFacetFieldExt);
            });
        }
        return enabledFacets;
    }

    private static boolean hasCustomFacets(TurSNSiteFieldExt turSNSiteFieldExt) {
        return !CollectionUtils.isEmpty(turSNSiteFieldExt.getCustomFacets());
    }

    private List<TurSNSiteFieldExt> getEnabledFields(TurSNSite turSNSite) {
        return turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite, 1);
    }

    private List<TurSNSiteFieldExt> getEnabledFacets(TurSNSite turSNSite) {
        return getEnabledFields(turSNSite).stream()
                .filter(field -> isFacetEnabled(field) || hasCustomFacets(field))
                .toList();
    }

    private static boolean isFacetEnabled(TurSNSiteFieldExt turSNSiteFieldExt) {
        return turSNSiteFieldExt.getFacet() == 1;
    }

    private static void setFacetSort(SolrQuery query, TurSNSiteFieldExt turSNSiteFacetFieldExt) {
        Optional.of(turSNSiteFacetFieldExt).map(TurSNSiteFieldExt::getFacetSort)
                .filter(field -> !turSNSiteFacetFieldExt.getFacetSort()
                        .equals(TurSNSiteFacetFieldSortEnum.DEFAULT))
                .ifPresent(
                        field -> query
                                .set("f." + turSNSiteFacetFieldExt.getName() + ".facet.sort",
                                        turSNSiteFacetFieldExt.getFacetSort().equals(
                                                TurSNSiteFacetFieldSortEnum.ALPHABETICAL) ? INDEX
                                                        : COUNT));
    }

    private static boolean facetSortIsEmptyOrCount(TurSNSite turSNSite) {
        return turSNSite.getFacetSort() == null
                || turSNSite.getFacetSort().equals(TurSNSiteFacetSortEnum.COUNT);
    }

    private void addFacetField(TurSNFacetTypeContext context, SolrQuery query) {
        query.addFacetField(setFacetTypeConditionInFacet(context,
                setEntityPrefix(context.getTurSNSiteFacetFieldExtDto())
                        .concat(context.getTurSNSiteFacetFieldExtDto().getName())));
    }

    private void addFacetRange(TurSNFacetTypeContext context, SolrQuery query) {
        String fieldName = context.getTurSNSiteFacetFieldExtDto().getName();
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.MONTH, 0);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        query.addDateRangeFacet(
                fieldName,
                DateUtils.addYears(cal.getTime(), -100),
                DateUtils.addYears(cal.getTime(), 100),
                PLUS_ONE + context.getTurSNSiteFacetFieldExtDto().getFacetRange());
    }

    private void addCustomFacetQuery(TurSNFacetTypeContext context, SolrQuery query,
            TurSNSiteFieldExt turSNSiteFieldExt) {
        turSNSiteFieldExt.getCustomFacets().stream()
                .map(customFacet -> new TurSNCustomFacetDefinition(turSNSiteFieldExt, customFacet,
                        null))
                .filter(customFacet -> StringUtils.hasText(customFacet.getName()))
                .forEach(customFacet -> {
                    TurSNSiteFieldExtDto customFacetFieldExtDto = customFacet.toFacetFieldExtDto();
                    TurSNFacetTypeContext customFacetContext = new TurSNFacetTypeContext(
                            customFacetFieldExtDto, context.getTurSNSite(),
                            context.getTurSNFilterParams());
                    boolean excludeFacetValues = setFacetTypeConditionInFacet(customFacetContext,
                            turSNSiteFieldExt.getName(), customFacet.getName())
                            .startsWith(FACET_OR);
                    customFacet.getItems().stream()
                            .filter(item -> StringUtils.hasText(item.getLabel()))
                            .forEach(item -> query.addFacetQuery(getCustomFacetLocalParams(
                                    customFacet.getName(), item.getLabel(), excludeFacetValues)
                                    + setRangeFilterQuery(turSNSiteFieldExt.getName(),
                                            turSNSiteFieldExt.getType(), item)));
                });
    }

    private static String getCustomFacetLocalParams(String customFacetName,
            String itemLabel, boolean excludeFacetValues) {
        String key = String.format("%s%s%s", customFacetName,
                CUSTOM_FACET_QUERY_SEPARATOR, itemLabel);
        if (excludeFacetValues) {
            return String.format("{!key='%s' ex=_all_}", escapeLocalParamValue(key));
        }
        return String.format("{!key='%s'}", escapeLocalParamValue(key));
    }

    private static String escapeLocalParamValue(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String setRangeFilterQuery(String fieldName, TurSEFieldType fieldType,
            TurSNSiteCustomFacetItem item) {
        String start;
        String end;

        if (TurSEFieldType.DATE.equals(fieldType)) {
            start = Optional.ofNullable(item.getRangeStartDate())
                    .map(Instant::toString)
                    .orElse("*");
            end = Optional.ofNullable(item.getRangeEndDate())
                    .map(Instant::toString)
                    .orElse("*");
        } else {
            start = Optional.ofNullable(item.getRangeStart()).map(Number::toString).orElse("*");
            end = Optional.ofNullable(item.getRangeEnd()).map(Number::toString).orElse("*");
        }

        return String.format("%s:[%s TO %s]", fieldName, start, end);
    }

    private record CustomFacetFilterMapping(String customFacetName,
            TurSNSiteFieldExt turSNSiteFieldExt,
            TurSNSiteCustomFacet turSNSiteCustomFacet,
            TurSNSiteCustomFacetItem customFacetItem) {
    }

    @NotNull
    private static String setEntityPrefix(TurSNSiteFieldExtDto turSNSiteFacetFieldExtDto) {
        return isNerOrThesaurus(turSNSiteFacetFieldExtDto.getSnType()) ? TURING_ENTITY : EMPTY;
    }

    private static boolean isNerOrThesaurus(TurSNFieldType snType) {
        return Collections.unmodifiableSet(EnumSet.of(TurSNFieldType.NER, TurSNFieldType.THESAURUS))
                .contains(snType);
    }

    public List<TurSNSiteFieldExt> prepareQueryHL(TurSNSite turSNSite, SolrQuery query,
            TurSNSiteSearchContext context) {
        List<TurSNSiteFieldExt> turSNSiteHlFieldExtList = getHLFields(turSNSite);
        if (context.getTurSNConfig().isHlEnabled()) {
            StringBuilder hlFields = new StringBuilder();
            turSNSiteHlFieldExtList.forEach(turSNSiteHlFieldExt -> {
                if (!hlFields.isEmpty()) {
                    hlFields.append(",");
                }
                hlFields.append(turSNSiteHlFieldExt.getName());
            });
            query.setHighlight(true).setHighlightSnippets(1)
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

    private void setRows(TurSNSite turSNSite, TurSEParameters turSEParameters) {
        if (turSEParameters.getRows() < 0) {
            turSEParameters
                    .setRows((turSNSite.getRowsPerPage() > 0) ? turSNSite.getRowsPerPage() : 10);
        }
    }

    private void setSortEntry(TurSNSite turSNSite, SolrQuery query,
            TurSEParameters turSEParameters) {
        Optional.ofNullable(turSEParameters.getSort())
                .ifPresent(
                        sort -> TurCommonsUtils.getKeyValueFromColon(sort)
                                .ifPresentOrElse(
                                        kv -> query.setSort(kv.getKey(),
                                                kv.getValue().equals(ASC) ? SolrQuery.ORDER.asc : SolrQuery.ORDER.desc),
                                        () -> {
                                            if (sort.equalsIgnoreCase(NEWEST))
                                                query.setSort(turSNSite.getDefaultDateField(),
                                                        SolrQuery.ORDER.desc);
                                            else if (sort.equalsIgnoreCase(OLDEST))
                                                query.setSort(turSNSite.getDefaultDateField(),
                                                        SolrQuery.ORDER.asc);
                                        }));
    }

    private boolean hasMLT(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList) {
        return TurSNUtils.isTrue(turSNSite.getMlt())
                && !CollectionUtils.isEmpty(turSNSiteMLTFieldExtList);
    }

    private void prepareQueryTargetingRules(TurSNSitePostParamsBean turSNSitePostParamsBean,
            SolrQuery query) {
        if (isTargetingRulesWithoutCondition(turSNSitePostParamsBean)) {
            targetingRulesWithoutCondition(turSNSitePostParamsBean, query);
        } else if (isTargetingRulesWithCondition(turSNSitePostParamsBean)) {
            targetingRulesWithCondition(turSNSitePostParamsBean, query);
        }
    }

    private static boolean isTargetingRulesWithCondition(
            TurSNSitePostParamsBean turSNSitePostParamsBean) {
        return !CollectionUtils.isEmpty(turSNSitePostParamsBean.getTargetingRulesWithCondition())
                || !CollectionUtils
                        .isEmpty(turSNSitePostParamsBean.getTargetingRulesWithConditionAND())
                || !CollectionUtils
                        .isEmpty(turSNSitePostParamsBean.getTargetingRulesWithConditionOR());
    }

    private static boolean isTargetingRulesWithoutCondition(
            TurSNSitePostParamsBean turSNSitePostParamsBean) {
        return !CollectionUtils.isEmpty(turSNSitePostParamsBean.getTargetingRules());
    }

    private void targetingRulesWithoutCondition(TurSNSitePostParamsBean turSNSitePostParamsBean,
            SolrQuery query) {
        if (!CollectionUtils.isEmpty(turSNSitePostParamsBean.getTargetingRules()))
            query.addFilterQuery(turSNTargetingRules.ruleExpression(TurSNTargetingRuleMethod.AND,
                    turSNSitePostParamsBean.getTargetingRules()));
    }

    private void targetingRulesWithCondition(TurSNSitePostParamsBean turSNSitePostParamsBean,
            SolrQuery query) {
        Map<String, List<String>> formattedRulesAND = new HashMap<>();
        Map<String, List<String>> formattedRulesOR = new HashMap<>();
        Set<String> conditions = new HashSet<>();
        targetingRulesWithCondition(turSNSitePostParamsBean.getTargetingRulesWithCondition(),
                formattedRulesAND, conditions);
        targetingRulesWithCondition(turSNSitePostParamsBean.getTargetingRulesWithConditionAND(),
                formattedRulesAND, conditions);
        targetingRulesWithCondition(turSNSitePostParamsBean.getTargetingRulesWithConditionOR(),
                formattedRulesOR, conditions);
        List<String> rules = new ArrayList<>();
        conditions.forEach(condition -> {
            StringBuilder rule = new StringBuilder();
            boolean containAndRules = formattedRulesAND.containsKey(condition);
            boolean containOrRules = formattedRulesOR.containsKey(condition);
            if (containAndRules)
                rule.append(turSNTargetingRules.andMethod(formattedRulesAND.get(condition)));
            if (containOrRules) {
                rule.append(
                        containAndRules
                                ? String.format(" AND (%s)",
                                        turSNTargetingRules
                                                .orMethod(formattedRulesOR.get(condition)))
                                : turSNTargetingRules.orMethod(formattedRulesOR.get(condition)));
            }
            rules.add(String.format("( %s AND ( %s ) )", condition, rule));
        });

        String targetingRuleQuery = String.format("%s OR (*:* NOT ( %s ) )",
                String.join(betweenSpaces(OR), rules), String.join(betweenSpaces(OR), conditions));
        query.addFilterQuery(targetingRuleQuery);
    }

    private void addFormattedRules(Map<String, List<String>> formattedRules, String key,
            List<String> value) {
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

    private boolean wasFacetConfigured(TurSNSite turSNSite,
            List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList) {
        return TurSNUtils.isTrue(turSNSite.getFacet()) && turSNSite.getItemsPerFacet() != null
                && !CollectionUtils.isEmpty(turSNSiteFacetFieldExtList);
    }
}