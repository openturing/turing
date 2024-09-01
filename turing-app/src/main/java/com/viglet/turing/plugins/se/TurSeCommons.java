package com.viglet.turing.plugins.se;

import com.google.inject.Inject;
import com.viglet.turing.api.sn.bean.TurSNSiteFilterQueryBean;
import com.viglet.turing.commons.se.TurSEFilterQueryParameters;
import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.field.TurSEFieldType;
import com.viglet.turing.commons.se.similar.TurSESimilarResult;
import com.viglet.turing.commons.sn.bean.*;
import com.viglet.turing.commons.sn.bean.spellcheck.TurSNSiteSpellCheckBean;
import com.viglet.turing.commons.sn.pagination.TurSNPaginationType;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNParamType;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtDto;
import com.viglet.turing.persistence.dto.sn.field.TurSNSiteFieldExtFacetDto;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetEnum;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetRangeEnum;
import com.viglet.turing.persistence.model.sn.TurSNSiteFacetSortEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFacetFieldEnum;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteField;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExt;
import com.viglet.turing.persistence.model.sn.field.TurSNSiteFieldExtFacet;
import com.viglet.turing.persistence.model.sn.metric.TurSNSiteMetricAccess;
import com.viglet.turing.persistence.repository.se.TurSEInstanceRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtFacetRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.locale.TurSNSiteLocaleRepository;
import com.viglet.turing.persistence.repository.sn.metric.TurSNSiteMetricAccessRepository;
import com.viglet.turing.se.facet.TurSEFacetResult;
import com.viglet.turing.se.result.TurSEGenericResults;
import com.viglet.turing.se.result.TurSEGroup;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.sn.TurSNFieldType;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.sn.spotlight.TurSNSpotlightProcess;
import com.viglet.turing.solr.TurSolrField;
import com.viglet.turing.solr.TurSolrUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.solr.client.solrj.response.Group;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.tika.utils.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Component
public class TurSeCommons {
    public static final String AND = "AND";
    public static final String OR = "OR";
    public static final String EMPTY = "";
    public static final String TURING_ENTITY = "turing_entity_";
    public static final String PREVIOUS = "Previous";
    public static final String GROUP = "group";
    public static final String LAST = "Last";
    public static final String NEXT = "Next";
    public static final String FIRST = "First";
    public static final String LANGUAGE = "language";
    public static final String FACETS_TO_REMOVE = "Facets To Remove";
    public static final String ID = "id";
    private final TurSNSiteLocaleRepository turSNSiteLocaleRepository;
    private final TurSNSiteRepository turSNSiteRepository;
    private final TurSEInstanceRepository turSEInstanceRepository;
    private final ApplicationContext applicationContext;
    private final TurSNSiteFieldExtRepository turSNSiteFieldExtRepository;
    private final TurSNSpotlightProcess turSNSpotlightProcess;
    private final TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository;
    private final TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository;
    @Inject
    public TurSeCommons(TurSNSiteLocaleRepository turSNSiteLocaleRepository,
                        TurSNSiteRepository turSNSiteRepository,
                        TurSEInstanceRepository turSEInstanceRepository,
                        ApplicationContext applicationContext,
                        TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
                        TurSNSpotlightProcess turSNSpotlightProcess,
                        TurSNSiteMetricAccessRepository turSNSiteMetricAccessRepository,
                        TurSNSiteFieldExtFacetRepository turSNSiteFieldExtFacetRepository) {
        this.turSNSiteLocaleRepository = turSNSiteLocaleRepository;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSEInstanceRepository = turSEInstanceRepository;
        this.applicationContext = applicationContext;
        this.turSNSiteFieldExtRepository = turSNSiteFieldExtRepository;
        this.turSNSpotlightProcess = turSNSpotlightProcess;
        this.turSNSiteMetricAccessRepository = turSNSiteMetricAccessRepository;
        this.turSNSiteFieldExtFacetRepository = turSNSiteFieldExtFacetRepository;
    }

    public List<TurSNSiteLocaleBean> responseLocales(TurSNSite turSNSite, URI uri) {
        List<TurSNSiteLocaleBean> turSNSiteLocaleBeans = new ArrayList<>();
        turSNSiteLocaleRepository.findByTurSNSite(Sort.by(Sort.Order.asc(LANGUAGE).ignoreCase()), turSNSite)
                .forEach(turSNSiteLocale -> turSNSiteLocaleBeans.add(new TurSNSiteLocaleBean()
                        .setLocale(turSNSiteLocale.getLanguage())
                        .setLink(TurCommonsUtils
                                .addOrReplaceParameter(uri, TurSNParamType.LOCALE,
                                        turSNSiteLocale.getLanguage()).toString())));

        return turSNSiteLocaleBeans;
    }

    @Nullable
    public Optional<TurSeConnector> getTurSeConnector(TurSNSiteSearchContext turSNSiteSearchContext) {
        return getTurSeConnector(turSNSiteSearchContext.getSiteName());
    }

    @Nullable
    public Optional<TurSeConnector> getTurSeConnector(String siteName) {
        return turSNSiteRepository.findByName(siteName)
                .flatMap(turSNSite -> turSEInstanceRepository
                        .findById(turSNSite.getTurSEInstance().getId()).map(seInstance ->
                        {
                            try {
                                log.info("Plugin {}", seInstance.getTurSEVendor().getPlugin());
                                return (TurSeConnector) applicationContext
                                        .getBean(Class.forName(seInstance.getTurSEVendor().getPlugin()));
                            } catch (ClassNotFoundException e) {
                                log.error(e.getMessage(), e);
                                return null;
                            }
                        }));
    }

    public List<String> requestTargetingRules(List<String> tr) {
        List<String> targetingRuleModified = new ArrayList<>();
        if (!CollectionUtils.isEmpty(tr)) {
            tr.forEach(targetingRule -> {
                String[] targetingRuleParts = targetingRule.split(":");
                if (targetingRuleParts.length == 2) {
                    if (!targetingRuleParts[1].startsWith("\"") && !targetingRuleParts[1].startsWith("[")) {
                        targetingRuleParts[1] = "\"" + targetingRuleParts[1] + "\"";
                        targetingRuleModified.add(targetingRuleParts[0] + ":" + targetingRuleParts[1]);
                    }
                } else {
                    targetingRuleModified.add(targetingRule);
                }
            });
        }
        return targetingRuleModified;
    }

    public TurSNSiteSearchResultsBean responseDocuments(TurSeConnector turSeConnector,
                                                        TurSNSiteSearchContext context,
                                                        TurSNSite turSNSite,
                                                        Map<String, TurSNSiteFieldExtDto> facetMap,
                                                        List<TurSEResult> seResults) {
        Map<String, TurSNSiteFieldExtDto> fieldExtMap = new HashMap<>();
        turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite,
                        1).stream().map(TurSNSiteFieldExtDto::new).toList()
                .forEach(turSNSiteFieldExtDto -> fieldExtMap.put(turSNSiteFieldExtDto.getName(), turSNSiteFieldExtDto));
        List<TurSNSiteSearchDocumentBean> turSNSiteSearchDocumentsBean = new ArrayList<>();
        seResults.forEach(result -> TurSNUtils.addSNDocument(context.getUri(), fieldExtMap, facetMap,
                turSNSiteSearchDocumentsBean, result, false));
        Optional.ofNullable(turSNSite)
                .map(TurSNSite::getSpotlightWithResults)
                .filter(TurSeConnectorUtils::isTrue).ifPresent(r ->
                        turSNSpotlightProcess.addSpotlightToResults(context, turSeConnector, turSNSite, facetMap,
                                fieldExtMap, turSNSiteSearchDocumentsBean));
        return new TurSNSiteSearchResultsBean().setDocument(turSNSiteSearchDocumentsBean);
    }

    @NotNull
    public static TurSNSiteSearchFacetBean getTurSNSiteSearchFacetBean(TurSNSiteSearchContext context,
                                                                        TurSNSiteFieldExtDto turSNSiteFieldExtDto,
                                                                        List<TurSNSiteSearchFacetItemBean>
                                                                                turSNSiteSearchFacetItemBeans) {
        return new TurSNSiteSearchFacetBean()
                .setLabel(new TurSNSiteSearchFacetLabelBean()
                        .setLang(context.getLocale().toString())
                        .setText(getTurSNSiteFieldExtFacetDto(context, turSNSiteFieldExtDto).getLabel()))
                .setName(turSNSiteFieldExtDto.getName())
                .setDescription(turSNSiteFieldExtDto.getDescription())
                .setType(turSNSiteFieldExtDto.getType())
                .setFacets(turSNSiteSearchFacetItemBeans)
                .setMultiValued(TurSeConnectorUtils.isTrue(turSNSiteFieldExtDto.getMultiValued()));
    }

    public static TurSNSiteFieldExtFacetDto getTurSNSiteFieldExtFacetDto(TurSNSiteSearchContext context,
                                                                          TurSNSiteFieldExtDto turSNSiteFieldExtDto) {
        return turSNSiteFieldExtDto.getFacetLocales()
                .stream()
                .filter(o -> o.getLocale().toString().equals(context.getLocale().toString())).findFirst()
                .orElse(TurSNSiteFieldExtFacetDto.builder().locale(context.getLocale())
                        .label(turSNSiteFieldExtDto.getFacetName()).build());
    }

    @NotNull
    public static TurSNSiteSearchFacetBean getTurSNSiteSearchFacetBean(
            List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetToRemoveItemBeans) {
        return new TurSNSiteSearchFacetBean()
                .setLabel(new TurSNSiteSearchFacetLabelBean()
                        .setLang(TurSNUtils.DEFAULT_LANGUAGE)
                        .setText(FACETS_TO_REMOVE))
                .setFacets(turSNSiteSearchFacetToRemoveItemBeans);
    }

    public TurSNSiteSearchFacetBean responseFacetToRemove(TurSNSiteSearchContext context) {
        if (!CollectionUtils.isEmpty(context.getTurSEParameters().getFilterQueries().getFq())) {
            List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetToRemoveItemBeans = new ArrayList<>();
            context.getTurSEParameters().getFilterQueries().getFq().forEach(facetToRemove -> {
                String[] facetToRemoveParts = facetToRemove.split(":", 2);
                if (facetToRemoveParts.length == 2) {
                    turSNSiteSearchFacetToRemoveItemBeans.add(new TurSNSiteSearchFacetItemBean()
                            .setLabel(facetToRemoveParts[1].replace("\"", ""))
                            .setLink(TurSNUtils.removeFilterQuery(context.getUri(), facetToRemove).toString())
                            .setSelected(true));
                }
            });
            return getTurSNSiteSearchFacetBean(turSNSiteSearchFacetToRemoveItemBeans);
        }
        return new TurSNSiteSearchFacetBean();
    }

    public List<TurSNSiteSpotlightDocumentBean> responseSpotlights(TurSNSiteSearchContext context,
                                                                    TurSNSite turSNSite) {
        List<TurSNSiteSpotlightDocumentBean> turSNSiteSpotlightDocumentBeans = new ArrayList<>();
        turSNSpotlightProcess
                .getSpotlightsFromQuery(context, turSNSite)
                .forEach((key, value) -> value
                        .forEach(document -> turSNSiteSpotlightDocumentBeans.add(new TurSNSiteSpotlightDocumentBean()
                                .setId(document.getId())
                                .setContent(document.getContent())
                                .setLink(document.getLink())
                                .setPosition(document.getPosition())
                                .setReferenceId(document.getReferenceId())
                                .setTitle(document.getTitle())
                                .setType(document.getType()))));
        turSNSiteSpotlightDocumentBeans.sort(Comparator.comparingInt(TurSNSiteSpotlightDocumentBean::getPosition));
        return turSNSiteSpotlightDocumentBeans;
    }

    public TurSNSiteSearchWidgetBean responseWidget(TurSNSiteSearchContext context, TurSNSite turSNSite,
                                                     Map<String, TurSNSiteFieldExtDto> facetMap,
                                                     TurSEResults turSEResults) {
        return new TurSNSiteSearchWidgetBean()
                .setFacet(responseFacet(context, turSNSite,
                        requestFilterQuery(context.getTurSEParameters().getFilterQueries().getFq())
                                .getHiddenItems(), facetMap, turSEResults))
                .setFacetToRemove(responseFacetToRemove(context))
                .setSimilar(responseMLT(turSNSite, turSEResults))
                .setSpellCheck(new TurSNSiteSpellCheckBean(context, turSEResults.getSpellCheck()))
                .setLocales(responseLocales(turSNSite, context.getUri()))
                .setSpotlights(responseSpotlights(context, turSNSite));
    }

    public TurSNSiteSearchBean getSearchBeanForResults(TurSeConnector turSeConnector, TurSNSiteSearchContext context,
                                                       TurSEResults turSEResults, TurSNSite turSNSite,
                                                       Map<String, TurSNSiteFieldExtDto> facetMap) {
        return new TurSNSiteSearchBean()
                .setResults(responseDocuments(turSeConnector, context, turSNSite, facetMap,
                        turSEResults.getResults()))
                .setPagination(responsePagination(context.getUri(), turSEResults))
                .setWidget(responseWidget(context, turSNSite, facetMap, turSEResults))
                .setQueryContext(responseQueryContext(turSNSite, turSEResults,
                        context.getLocale()));
    }

    private List<TurSNSiteSearchPaginationBean> responsePagination(URI uri, TurSEGenericResults turSEResults) {
        List<TurSNSiteSearchPaginationBean> pagination = new ArrayList<>();
        if (turSEResults.getCurrentPage() > 1) {
            pagination.add(setFirstPage(uri));
            if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
                pagination.add(setPreviousPage(uri, turSEResults));
            }
        }
        IntStream.rangeClosed(getFirstPagination(turSEResults), getLastPagination(turSEResults)).forEach(page ->
                pagination.add(isCurrentPage(turSEResults, page) ? setCurrentPage(uri, page) :
                        setOtherPages(uri, page)));
        if (isNotLastPage(turSEResults)) {
            if (turSEResults.getCurrentPage() <= turSEResults.getPageCount()) {
                pagination.add(setNextPage(uri, turSEResults));
            }
            pagination.add(setLastPage(uri, turSEResults));
        }
        return pagination;
    }

    private TurSNSiteSearchPaginationBean setOtherPages(URI uri, int page) {
        return setGenericPages(uri, page, TurSNPaginationType.PAGE);
    }

    private TurSNSiteSearchPaginationBean setCurrentPage(URI uri, int page) {
        return setGenericPages(uri, page, TurSNPaginationType.CURRENT);
    }

    private int getLastPagination(TurSEGenericResults turSEResults) {
        return Math.min(turSEResults.getCurrentPage() + 3, turSEResults.getPageCount());
    }

    private static boolean isCurrentPage(TurSEGenericResults turSEResults, int page) {
        return page == turSEResults.getCurrentPage();
    }

    private static boolean isNotLastPage(TurSEGenericResults turSEResults) {
        return turSEResults.getCurrentPage() != turSEResults.getPageCount() && turSEResults.getPageCount() > 1;
    }

    private TurSNSiteSearchPaginationBean setFirstPage(URI uri) {
        return new TurSNSiteSearchPaginationBean()
                .setType(TurSNPaginationType.FIRST)
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(1))
                        .toString())
                .setText(FIRST)
                .setPage(1);
    }

    private TurSNSiteSearchPaginationBean setPreviousPage(URI uri, TurSEGenericResults turSEResults) {
        return new TurSNSiteSearchPaginationBean()
                .setType(TurSNPaginationType.PREVIOUS)
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE,
                        Integer.toString(turSEResults.getCurrentPage() - 1)).toString())
                .setText(PREVIOUS)
                .setPage(turSEResults.getCurrentPage() - 1);
    }


    private TurSNSiteSearchPaginationBean setLastPage(URI uri, TurSEGenericResults turSEResults) {
        return new TurSNSiteSearchPaginationBean()
                .setType(TurSNPaginationType.LAST)
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE,
                        Integer.toString(turSEResults.getPageCount())).toString())
                .setText(LAST)
                .setPage(turSEResults.getPageCount());
    }

    private TurSNSiteSearchPaginationBean setNextPage(URI uri, TurSEGenericResults turSEResults) {
        return new TurSNSiteSearchPaginationBean()
                .setType(TurSNPaginationType.NEXT)
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE,
                        Integer.toString(turSEResults.getCurrentPage() + 1)).toString())
                .setText(NEXT)
                .setPage(turSEResults.getCurrentPage() + 1);
    }

    public void populateMetrics(TurSNSite turSNSite, TurSNSiteSearchContext turSNSiteSearchContext, long numFound) {
        if (!turSNSiteSearchContext.getTurSEParameters().getQuery().trim().equals("*")
                && useMetrics(turSNSiteSearchContext)) {
            TurSNSiteMetricAccess turSNSiteMetricAccess = new TurSNSiteMetricAccess();
            turSNSiteMetricAccess.setAccessDate(new Date());
            turSNSiteMetricAccess.setLanguage(turSNSiteSearchContext.getLocale());
            turSNSiteMetricAccess.setTerm(turSNSiteSearchContext.getTurSEParameters().getQuery());
            turSNSiteMetricAccess.setTurSNSite(turSNSite);
            turSNSiteMetricAccess.setNumFound(numFound);
            Optional.ofNullable(turSNSiteSearchContext.getTurSNSitePostParamsBean()).ifPresent(p -> {
                turSNSiteMetricAccess.setTargetingRules(Optional.ofNullable(p.getTargetingRules()).map(HashSet::new)
                        .orElse(new HashSet<>()));
                turSNSiteMetricAccess.setUserId(p.getUserId());
            });
            turSNSiteMetricAccessRepository.save(turSNSiteMetricAccess);
        }
    }

    private boolean useMetrics(TurSNSiteSearchContext turSNSiteSearchContext) {
        return turSNSiteSearchContext.getTurSNSitePostParamsBean() == null
                || turSNSiteSearchContext.getTurSNSitePostParamsBean().isPopulateMetrics();
    }

    public TurSNSiteSearchBean getSearchBeanForGroup(TurSeConnector turSeConnector,
                                                     TurSNSiteSearchContext context,
                                                     TurSEResults turSEResults, TurSNSite turSNSite,
                                                     Map<String, TurSNSiteFieldExtDto> facetMap) {
        return new TurSNSiteSearchBean()
                .setGroups(responseGroups(turSeConnector, context, turSNSite, facetMap, turSEResults))
                .setWidget(responseWidget(context, turSNSite, facetMap, turSEResults))
                .setQueryContext(responseQueryContext(turSNSite, turSEResults,
                        context.getLocale()));
    }

    private List<TurSNSiteSearchGroupBean> responseGroups(TurSeConnector turSeConnector,
                                                          TurSNSiteSearchContext context,
                                                          TurSNSite turSNSite,
                                                          Map<String, TurSNSiteFieldExtDto> facetMap,
                                                          TurSEResults turSEResults) {
        List<TurSNSiteSearchGroupBean> turSNSiteSearchGroupBeans = new ArrayList<>();
        Optional.ofNullable(turSEResults.getGroups()).ifPresent(g -> g.forEach(group -> {
            int lastItemOfFullPage = (int) group.getStart() + group.getLimit();
            int firstItemOfFullPage = (int) group.getStart() + 1;
            int count = (int) group.getNumFound();
            int pageEnd = Math.min(lastItemOfFullPage, count);
            turSNSiteSearchGroupBeans.add(new TurSNSiteSearchGroupBean()
                    .setName(group.getName())
                    .setCount((int) group.getNumFound())
                    .setPageCount(group.getPageCount())
                    .setPage(group.getCurrentPage())
                    .setCount(count)
                    .setPageEnd(pageEnd)
                    .setPageStart(Math.min(firstItemOfFullPage, pageEnd))
                    .setLimit(group.getLimit())
                    .setPagination(responsePagination(changeGroupURIForPagination(context.getUri(),
                            group.getName()), group))
                    .setResults(responseDocuments(turSeConnector, context, turSNSite, facetMap,
                            group.getResults())));
        }));
        return turSNSiteSearchGroupBeans;
    }
    private TurSNSiteSearchQueryContextBean responseQueryContext(TurSNSite turSNSite, TurSEResults turSEResults,
                                                                 Locale locale) {
        int lastItemOfFullPage = (int) turSEResults.getStart() + turSEResults.getLimit();
        int firstItemOfFullPage = (int) turSEResults.getStart() + 1;
        int count = (int) turSEResults.getNumFound();
        int pageEnd = Math.min(lastItemOfFullPage, count);
        return new TurSNSiteSearchQueryContextBean()
                .setQuery(new TurSNSiteSearchQueryContextQueryBean()
                        .setQueryString(turSEResults.getQueryString())
                        .setSort(turSEResults.getSort())
                        .setLocale(locale))
                .setDefaultFields(defaultFields(turSNSite))
                .setPageCount(turSEResults.getPageCount())
                .setPage(turSEResults.getCurrentPage())
                .setCount(count)
                .setPageEnd(pageEnd)
                .setPageStart(Math.min(firstItemOfFullPage, pageEnd))
                .setLimit(turSEResults.getLimit())
                .setOffset(0)
                .setResponseTime(turSEResults.getElapsedTime())
                .setIndex(turSNSite.getName())
                .setFacetType(turSNSite.getFacetType().toString());
    }

    private TurSNSiteSearchDefaultFieldsBean defaultFields(TurSNSite turSNSite) {
        return new TurSNSiteSearchDefaultFieldsBean()
                .setDate(turSNSite.getDefaultDateField())
                .setDescription(turSNSite.getDefaultDescriptionField())
                .setImage(turSNSite.getDefaultImageField())
                .setText(turSNSite.getDefaultTextField())
                .setTitle(turSNSite.getDefaultTitleField())
                .setUrl(turSNSite.getDefaultURLField());
    }

    private URI changeGroupURIForPagination(URI uri, String fieldName) {
        return TurCommonsUtils.addOrReplaceParameter(TurSNUtils.removeQueryField(uri, GROUP),
                TurSNParamType.FILTER_QUERIES_DEFAULT, fieldName);
    }

    private int getFirstPagination(TurSEGenericResults turSEResults) {
        return turSEResults.getCurrentPage() - 3 > 0 ? turSEResults.getCurrentPage() - 3 : 1;
    }

    private TurSNSiteSearchPaginationBean setGenericPages(URI uri, int page,
                                                          TurSNPaginationType type) {
        return new TurSNSiteSearchPaginationBean()
                .setHref(TurCommonsUtils.addOrReplaceParameter(uri, TurSNParamType.PAGE, Integer.toString(page))
                        .toString())
                .setText(Integer.toString(page))
                .setType(type)
                .setPage(page);
    }

    private List<TurSESimilarResult> responseMLT(TurSNSite turSNSite, TurSEResults turSEResults) {
        return hasMLT(turSNSite, turSEResults) ? turSEResults.getSimilarResults() : Collections.emptyList();
    }

    private boolean hasMLT(TurSNSite turSNSite, TurSEResults turSEResults) {
        return TurSeConnectorUtils.isTrue(turSNSite.getMlt()) && turSEResults.getSimilarResults() != null
                && !turSEResults.getSimilarResults().isEmpty();
    }

    private TurSNSiteFilterQueryBean requestFilterQuery(List<String> fq) {
        List<String> hiddenFilterQuery = new ArrayList<>();
        List<String> filterQueryModified = new ArrayList<>();
        processFilterQuery(fq, hiddenFilterQuery, filterQueryModified);
        return new TurSNSiteFilterQueryBean()
                .setHiddenItems(hiddenFilterQuery)
                .setItems(filterQueryModified);
    }

    private List<TurSNSiteSearchFacetBean> responseFacet(TurSNSiteSearchContext context,
                                                         TurSNSite turSNSite, List<String> hiddenFilterQuery,
                                                         Map<String, TurSNSiteFieldExtDto> facetMap,
                                                         TurSEResults turSEResults) {
        if (turSNSite.getFacet() == 1 && Optional.ofNullable(turSEResults.getFacetResults()).isPresent()) {
            List<String> usedFacetItems = Optional.ofNullable(context.getTurSEParameters())
                    .map(TurSEParameters::getFilterQueries)
                    .map(TurSEFilterQueryParameters::getFq)
                    .orElse(Collections.emptyList());
            List<TurSNSiteSearchFacetBean> turSNSiteSearchFacetBeans = new ArrayList<>();

            turSEResults.getFacetResults().forEach(facet -> {
                if (showFacet(hiddenFilterQuery, facetMap, facet, turSNSite)) {
                    List<TurSNSiteSearchFacetItemBean> turSNSiteSearchFacetItemBeans = new ArrayList<>();
                    facet.getTurSEFacetResultAttr().values().forEach(facetItem -> {
                        final String fq = facet.getFacet() + ":" + facetItem.getAttribute();
                        turSNSiteSearchFacetItemBeans.add(new TurSNSiteSearchFacetItemBean()
                                .setCount(facetItem.getCount())
                                .setLabel(facetItem.getAttribute())
                                .setLink(TurSNUtils
                                        .addFilterQuery(context.getUri(), fq).toString()
                                        .replace(":", "\\:"))
                                .setSelected(usedFacetItems.contains(fq))
                        );

                    });
                    turSNSiteSearchFacetBeans.add(TurSeCommons.getTurSNSiteSearchFacetBean(context,
                            facetMap.get(facet.getFacet()),
                            turSNSiteSearchFacetItemBeans));
                }
            });
            return turSNSiteSearchFacetBeans;
        }
        return Collections.emptyList();
    }

    private void processFilterQuery(List<String> fq, List<String> hiddenFilterQuery, List<String> filterQueryModified) {
        if (!CollectionUtils.isEmpty(fq)) {
            fq.forEach(filterQuery -> {
                String[] filterParts = filterQuery.split(":");
                if (filterParts.length == 2) {
                    addHiddenFilterQuery(hiddenFilterQuery, filterParts);
                    if (!filterParts[1].startsWith("\"") && !filterParts[1].startsWith("[")) {
                        filterParts[1] = "\"" + filterParts[1] + "\"";
                        filterQueryModified.add(filterParts[0] + ":" + filterParts[1]);
                    }
                } else {
                    filterQueryModified.add(filterQuery);
                }
            });
        }
    }

    private void addHiddenFilterQuery(List<String> hiddenFilterQuery, String[] filterParts) {
        if (!hiddenFilterQuery.contains(filterParts[0])) {
            hiddenFilterQuery.add(filterParts[0]);
        }
    }

    private static boolean showFacet(List<String> hiddenFilterQuery,
                                     Map<String, TurSNSiteFieldExtDto> facetMap,
                                     TurSEFacetResult facet, TurSNSite turSNSite) {
        return facetMap.containsKey(facet.getFacet())
                && (!hiddenFilterQuery.contains(facet.getFacet()) || showFacetByFacetType(turSNSite))
                && !facet.getTurSEFacetResultAttr().isEmpty();
    }

    private static boolean showFacetByFacetType(TurSNSite turSNSite) {
        if (turSNSite.getFacetType() == null) return false;
        else return switch (turSNSite.getFacetType()) {
            case OR -> true;
            case AND -> false;
        };
    }

    private static boolean isTuringEntity(TurSNFieldType snType) {
        return Collections.unmodifiableSet(EnumSet.of(TurSNFieldType.NER, TurSNFieldType.THESAURUS)).contains(snType);
    }

    public Map<String, TurSNSiteFieldExtDto> setFacetMap(List<TurSNSiteFieldExtDto> turSNSiteFacetFieldExtList) {
        Map<String, TurSNSiteFieldExtDto> facetMap = new HashMap<>();
        turSNSiteFacetFieldExtList.forEach(turSNSiteFacetFieldExt -> {
            if (isTuringEntity(turSNSiteFacetFieldExt.getSnType())) {
                facetMap.put(String.format("%s_%s", TurSNUtils.TURING_ENTITY, turSNSiteFacetFieldExt.getName()),
                        turSNSiteFacetFieldExt);
            }
            facetMap.put(turSNSiteFacetFieldExt.getName(), turSNSiteFacetFieldExt);
        });

        return facetMap;
    }

    public Set<TurSNSiteFieldExtFacet> getFacetLocales(TurSNSiteSearchContext context,
                                                        TurSNSiteFieldExt turSNSiteFieldExt) {
        return new HashSet<>(
                Collections.singletonList(turSNSiteFieldExtFacetRepository
                        .findByTurSNSiteFieldExtAndLocale(turSNSiteFieldExt,
                                context.getLocale())
                        .stream().findFirst()
                        .orElse(TurSNSiteFieldExtFacet.builder()
                                .locale(context.getLocale())
                                .label(turSNSiteFieldExt.getFacetName())
                                .build())));
    }


    @NotNull
    public List<TurSNSiteFieldExtDto> getTurSNSiteFieldExtDtoList(TurSNSiteSearchContext context, TurSNSite turSNSite) {
        return turSNSiteFieldExtRepository
                .findByTurSNSiteAndFacetAndEnabled(turSNSite, 1, 1).stream()
                .map(turSNSiteFieldExt -> {
                    TurSNSiteFieldExtDto turSNSiteFieldExtDto = new TurSNSiteFieldExtDto(turSNSiteFieldExt);
                    turSNSiteFieldExtDto.setFacetLocales(getFacetLocales(context, turSNSiteFieldExt));
                    return turSNSiteFieldExtDto;
                }).toList();
    }
    public boolean hasGroup(TurSEParameters turSEParameters) {
        return !StringUtils.isEmpty(turSEParameters.getGroup());
    }

    public TurSNSiteSearchBean searchResponse(TurSeConnector turSeConnector, TurSNSiteSearchContext context,
                                              TurSEResults turSEResults) {
        return turSNSiteRepository.findByName(context.getSiteName()).map(turSNSite -> {
                    populateMetrics(turSNSite, context, turSEResults.getNumFound());
                    Map<String, TurSNSiteFieldExtDto> facetMap = setFacetMap(getTurSNSiteFieldExtDtoList(context, turSNSite));
                    if (hasGroup(context.getTurSEParameters())) {
                        return getSearchBeanForGroup(turSeConnector, context, turSEResults, turSNSite,
                                facetMap);
                    } else {
                        return getSearchBeanForResults(turSeConnector, context, turSEResults,
                                turSNSite, facetMap);
                    }
                })
                .orElse(new TurSNSiteSearchBean());
    }

    // Convert to String with concatenated attributes
    public String concatenateString(@SuppressWarnings("rawtypes") List list) {
        int i = 0;
        StringBuilder sb = new StringBuilder();
        for (Object valueItem : list) {
            sb.append(TurSolrField.convertFieldToString(valueItem));
            // Last Item
            if (i++ != list.size() - 1) sb.append(System.lineSeparator());
        }
        return sb.toString().trim();
    }

    public static boolean enabledWildcardNoResults(TurSNSite turSNSite) {
        return turSNSite.getWildcardNoResults() != null
                && turSNSite.getWildcardNoResults() == 1;
    }

    public static boolean enabledWildcardAlways(TurSNSite turSNSite) {
        return turSNSite.getWildcardAlways() != null
                && turSNSite.getWildcardAlways() == 1;
    }

    public static Optional<TurSEGroup> seGroupsHasGroup(List<TurSEGroup> turSEGroups, Group group) {
        return turSEGroups.stream().filter(o -> o.getName() != null && group.getGroupValue() != null
                && o.getName().equals(group.getGroupValue())).findFirst();
    }

    public void setMLT(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList, TurSEResults turSEResults,
                       List<TurSESimilarResult> similarResults) {
        if (hasMLT(turSNSite, turSNSiteMLTFieldExtList)) turSEResults.setSimilarResults(similarResults);
    }

    public void setRows(TurSNSite turSNSite, TurSEParameters turSEParameters) {
        if (turSEParameters.getRows() <= 0) turSEParameters.setRows(turSNSite.getRowsPerPage());
    }

    public int getNumberOfPages(TurSEGenericResults turSEGenericResults) {
        return getNumberOfPages(turSEGenericResults.getNumFound(), turSEGenericResults.getLimit());
    }

    public int getNumberOfPages(long numFound, int limit) {
        return (int) Math.ceil(numFound / (double) limit);
    }
    public boolean hasMLT(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteMLTFieldExtList) {
        return turSNSite.getMlt() == 1 && !org.springframework.util.CollectionUtils.isEmpty(turSNSiteMLTFieldExtList);
    }

    public static boolean isHLAttribute(Map<String, TurSNSiteFieldExt> fieldExtMap, Map<String,
            List<String>> hl, String attribute) {
        return fieldExtMap.containsKey(attribute) &&
                (Collections.unmodifiableSet(EnumSet.of(TurSEFieldType.TEXT, TurSEFieldType.STRING))
                        .contains(fieldExtMap.get(attribute).getType())) &&
                hl != null && hl.containsKey(attribute);
    }

    public List<TurSNSiteFieldExt> getHLFields(TurSNSite turSNSite) {
        return turSNSiteFieldExtRepository.findByTurSNSiteAndHlAndEnabled(turSNSite, 1, 1);
    }

    public Map<String, List<String>> getHL(TurSNSite
                                                   turSNSite, List<TurSNSiteFieldExt> turSNSiteHlFieldExtList,
                                           QueryResponse queryResponse, SolrDocument document) {
        return isHL(turSNSite, turSNSiteHlFieldExtList) &&
                queryResponse.getHighlighting() != null ?
                queryResponse.getHighlighting().get(document.get(ID).toString()) : null;
    }

    public static boolean isHL(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteHlFieldExtList) {
        return turSNSite.getHl() == 1 &&
                !org.springframework.util.CollectionUtils.isEmpty(turSNSiteHlFieldExtList);
    }

    public Map<String, TurSNSiteFieldExt> getFieldExtMap(TurSNSite turSNSite) {
        return turSNSiteFieldExtRepository.findByTurSNSiteAndEnabled(turSNSite,
                1).stream().collect(Collectors
                .toMap(TurSNSiteFieldExt::getName, turSNSiteFieldExt -> turSNSiteFieldExt, (a, b) -> b));
    }

    public Map<String, Object> getRequiredFields(TurSNSite turSNSite) {
        return turSNSiteFieldExtRepository
                .findByTurSNSiteAndRequiredAndEnabled(turSNSite, 1, 1)
                .stream().filter(Objects::nonNull)
                .collect(Collectors
                        .toMap(TurSNSiteFieldExt::getName, TurSNSiteFieldExt::getDefaultValue, (a, b) -> b));
    }

    @NotNull
    public static String setEntityPrefix(TurSNSiteFieldExt turSNSiteFacetFieldExt) {
        return isNerOrThesaurus(turSNSiteFacetFieldExt.getSnType()) ? TURING_ENTITY : EMPTY;
    }

    private static boolean isNerOrThesaurus(TurSNFieldType snType) {
        return Collections.unmodifiableSet(EnumSet.of(TurSNFieldType.NER, TurSNFieldType.THESAURUS)).contains(snType);
    }

    public static boolean isMultiValued(Map<String, TurSNSiteField> turSNSiteFieldMap, String key) {
        return turSNSiteFieldMap.get(key) != null && turSNSiteFieldMap.get(key).getMultiValued() == 1;
    }

    public static boolean wasFacetConfigured(TurSNSite turSNSite, List<TurSNSiteFieldExt> turSNSiteFacetFieldExtList) {
        return turSNSite.getFacet() == 1 && !org.springframework.util.CollectionUtils.isEmpty(turSNSiteFacetFieldExtList);
    }

    public static boolean isTargetingRulesWithCondition(TurSNSitePostParamsBean turSNSitePostParamsBean) {
        return !org.springframework.util.CollectionUtils.isEmpty(turSNSitePostParamsBean.getTargetingRulesWithCondition()) ||
                !org.springframework.util.CollectionUtils.isEmpty(turSNSitePostParamsBean.getTargetingRulesWithConditionAND()) ||
                !org.springframework.util.CollectionUtils.isEmpty(turSNSitePostParamsBean.getTargetingRulesWithConditionOR());
    }

    public static boolean isTargetingRulesWithoutCondition(TurSNSitePostParamsBean turSNSitePostParamsBean) {
        return !org.springframework.util.CollectionUtils.isEmpty(turSNSitePostParamsBean.getTargetingRules());
    }
    private void addFormattedRules(Map<String, List<String>> formattedRules, String key, List<String> value) {
        if (formattedRules.containsKey(key))
            formattedRules.get(key).addAll(value);
        else
            formattedRules.put(key, value);
    }
    public void targetingRulesWithCondition(Map<String, List<String>> targetingRulesWithCondition,
                                            Map<String, List<String>> formattedRules, Set<String> conditions) {
        if (!org.springframework.util.CollectionUtils.isEmpty(targetingRulesWithCondition)) {
            targetingRulesWithCondition.forEach((key, value) -> {
                conditions.add(key);
                addFormattedRules(formattedRules, key, value);
            });
        }
    }

    @NotNull
    public static StringBuilder setFilterQueryString(Map<TurSNSiteFacetFieldEnum, List<String>> filterQueryMapModified) {
        StringBuilder filterQueryString = new StringBuilder();
        if (filterQueryMapModified.containsKey(TurSNSiteFacetFieldEnum.OR)) {
            filterQueryString.append(String.format("(%s)",
                    String.join(betweenSpaces(OR), filterQueryMapModified.get(TurSNSiteFacetFieldEnum.OR))));
            if (filterQueryMapModified.containsKey(TurSNSiteFacetFieldEnum.AND)) {
                filterQueryString.append(betweenSpaces(AND));
            }
        }
        if (filterQueryMapModified.containsKey(TurSNSiteFacetFieldEnum.AND)) {
            filterQueryString.append(String.join(betweenSpaces(AND),
                    filterQueryMapModified.get(TurSNSiteFacetFieldEnum.AND)));
        }
        return filterQueryString;
    }

    public static String betweenSpaces(String operator) {
        return " %s ".formatted(operator);
    }

    @NotNull
    public static List<String> getFilterQueryValue(List<String> value) {
        return value.stream()
                .map(fq -> queryWithoutExpression(fq) ? addDoubleQuotesToValue(fq) : fq
                ).toList();
    }

    public Map<TurSNSiteFacetFieldEnum, List<String>> getFilterQueryMap(
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

    public static TurSNSiteFacetFieldEnum getFacetType(TurSNSiteFieldExt facet, TurSNSite turSNSite,
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

    public static boolean isDateRangeFacet(TurSNSiteFieldExt dateFacetItem) {
        return dateFacetItem.getType().equals(TurSEFieldType.DATE)
                && dateFacetItem.getFacetRange() != null
                && !dateFacetItem.getFacetRange().equals(TurSNSiteFacetRangeEnum.DISABLED);
    }

    public static boolean isOr(TurSNFilterQueryOperator operator, TurSNSite turSNSite) {
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

    public static boolean facetSortIsEmptyOrCount(TurSNSite turSNSite) {
        return turSNSite.getFacetSort() == null || turSNSite.getFacetSort().equals(TurSNSiteFacetSortEnum.COUNT);
    }
}
