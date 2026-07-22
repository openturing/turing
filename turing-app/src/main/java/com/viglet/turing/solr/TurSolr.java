package com.viglet.turing.solr;

import static com.viglet.turing.solr.TurSolrConstants.EMPTY;
import static com.viglet.turing.solr.TurSolrConstants.ID;
import static com.viglet.turing.solr.TurSolrConstants.QUERY;
import static com.viglet.turing.solr.TurSolrConstants.TUR_SPELL;
import static com.viglet.turing.solr.TurSolrConstants.TUR_SUGGEST;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.request.QueryRequest;
import org.apache.solr.client.solrj.request.SolrQuery;
import org.apache.solr.client.solrj.request.SolrQuery.ORDER;
import org.apache.solr.client.solrj.response.GroupResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.response.SpellCheckResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.se.result.spellcheck.TurSESpellCheckResult;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.commons.utils.TurCommonsUtils;
import com.viglet.turing.persistence.model.sn.TurSNSite;
import com.viglet.turing.persistence.repository.profile.TurProfileRepository;
import com.viglet.turing.persistence.repository.sn.TurSNSiteRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteCustomFacetRepository;
import com.viglet.turing.persistence.repository.sn.sort.TurSNSiteCustomSortRepository;
import com.viglet.turing.persistence.repository.sn.field.TurSNSiteFieldExtRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingConditionRepository;
import com.viglet.turing.persistence.repository.sn.ranking.TurSNRankingExpressionRepository;
import com.viglet.turing.se.result.TurSEResult;
import com.viglet.turing.se.result.TurSEResults;
import com.viglet.turing.observability.TurSearchPipelineObservation;
import com.viglet.turing.profile.TurProfileFieldConfigService;
import com.viglet.turing.sn.TurSNFieldProcess;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.sn.searchrule.TurSNSearchRuleEvaluator;
import com.viglet.turing.sn.field.TurSNSiteFieldService;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService;
import com.viglet.turing.sn.tr.TurSNTargetingRules;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Transactional
public class TurSolr {

    /**
     * Lucene query-syntax characters that must be escaped in a spellcheck term.
     * Whitespace and the double-quote (stripped separately) are intentionally excluded.
     */
    private static final Pattern SPELL_CHECK_SPECIAL_CHARS =
            Pattern.compile("[+\\-!(){}\\[\\]^~*?:\\\\/&|]");

    /**
     * Extracts the field name from a Solr {@code undefined field: "name"} rejection message.
     * Handles both quoted ({@code undefined field: "catedras"}) and unquoted variants.
     */
    private static final Pattern UNDEFINED_FIELD =
            Pattern.compile("undefined field:?\\s*\"?([^\"\\s]+)\"?");

    private final TurSNSiteRepository turSNSiteRepository;
    private final boolean isCommitEnabled;
    private final TurSolrDocumentHandler turSolrDocumentHandler;
    private final TurSolrQueryBuilder turSolrQueryBuilder;
    private final TurSolrResultProcessor turSolrResultProcessor;

    public TurSolr(@Value("${turing.solr.commit.enabled:false}") boolean isCommitEnabled,
            @Value("${turing.solr.commit.within:10000}") int commitWithin,
            TurSNSiteFieldExtRepository turSNSiteFieldExtRepository,
            TurSNSiteCustomFacetRepository turSNSiteCustomFacetRepository,
            TurSNSiteCustomSortRepository turSNSiteCustomSortRepository,
            TurSNTargetingRules turSNTargetingRules, TurSNSiteFieldService turSNSiteFieldUtils,
            TurSNRankingExpressionRepository turSNRankingExpressionRepository,
            TurSNRankingConditionRepository turSNRankingConditionRepository,
            TurSNSiteRepository turSNSiteRepository, TurSNFieldProcess turSNFieldProcess,
            TurDecimalFieldNormalizer turDecimalFieldNormalizer,
            TurSNSearchRuleEvaluator turSNSearchRuleEvaluator,
            TurSNSiteSearchSnapshotService snapshotService,
            TurSearchPipelineObservation pipelineObservation,
            TurProfileRepository turProfileRepository,
            TurProfileFieldConfigService turProfileFieldConfigService) {
        this.isCommitEnabled = isCommitEnabled;
        this.turSNSiteRepository = turSNSiteRepository;
        this.turSolrDocumentHandler = new TurSolrDocumentHandler(commitWithin, turSNSiteFieldUtils,
                turDecimalFieldNormalizer);
        this.turSolrQueryBuilder = new TurSolrQueryBuilder(turSNSiteFieldExtRepository,
                turSNSiteCustomFacetRepository, turSNSiteCustomSortRepository,
                turSNRankingExpressionRepository, turSNRankingConditionRepository, turSNTargetingRules,
                turSNSearchRuleEvaluator, snapshotService, turProfileRepository,
                turProfileFieldConfigService);
        this.turSolrResultProcessor = new TurSolrResultProcessor(turSNFieldProcess,
                turSNSiteFieldExtRepository, turSNSiteCustomFacetRepository, pipelineObservation);
    }

    public String dslQuery(TurSolrInstance turSolrInstance, String jsonQuery) {
        RestTemplate restTemplate = new RestTemplate();
        String solrUrl = "%s/query".formatted(turSolrInstance.getSolrUrl());
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(jsonQuery, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(solrUrl, requestEntity, String.class);
        JSONObject jsonObject = new JSONObject(response.getBody());
        return jsonObject.getJSONObject("response").toString();
    }

    public long getDocumentTotal(TurSolrInstance turSolrInstance) {
        try {
            return executeSolrQuery(turSolrInstance, new SolrQuery().setQuery("*:*").setRows(0))
                    .map(queryResponse -> queryResponse.getResults().getNumFound()).orElse(0L);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return 0L;
        }
    }

    public void indexing(TurSolrInstance turSolrInstance, TurSNSite turSNSite,
            Map<String, Object> attributes) {
        turSolrDocumentHandler.indexing(turSolrInstance, turSNSite, attributes);
    }

    public int indexingBatch(TurSolrInstance turSolrInstance, TurSNSite turSNSite,
            List<Map<String, Object>> documents) {
        return turSolrDocumentHandler.indexingBatch(turSolrInstance, turSNSite, documents);
    }

    public void deIndexing(TurSolrInstance turSolrInstance, String id) {
        turSolrDocumentHandler.deIndexing(turSolrInstance, id);
    }

    public void deIndexingByType(TurSolrInstance turSolrInstance, String type) {
        turSolrDocumentHandler.deIndexingByType(turSolrInstance, type);
    }

    public SolrDocumentList solrResultAnd(TurSolrInstance turSolrInstance,
            Map<String, Object> attributes) {
        return executeSolrQuery(turSolrInstance,
                new SolrQuery().setQuery("*:*")
                        .setFilterQueries(attributes.entrySet().stream()
                                .map(entry -> entry.getKey() + ":\"" + entry.getValue() + "\"")
                                .toArray(String[]::new)))
                .map(QueryResponse::getResults)
                .orElse(new SolrDocumentList());
    }

    public SpellCheckResponse autoComplete(TurSolrInstance turSolrInstance, String term) {
        return executeSolrQuery(turSolrInstance,
                new SolrQuery().setParam("qt", TUR_SUGGEST).setQuery(term))
                .map(QueryResponse::getSpellCheckResponse).orElse(null);
    }

    public TurSESpellCheckResult spellCheckTerm(TurSolrInstance turSolrInstance, String term) {
        return executeSolrQuery(turSolrInstance, new SolrQuery().setParam("qt", TUR_SPELL)
                .setQuery(escapeSpellCheckTerm(term))).map(
                        queryResponse -> Optional.ofNullable(queryResponse.getSpellCheckResponse())
                                .map(spellCheckResponse -> {
                                    String correctedText = spellCheckResponse.getCollatedResult();
                                    if (StringUtils.isNotEmpty(correctedText)) {
                                        return new TurSESpellCheckResult(true, correctedText);
                                    }
                                    return new TurSESpellCheckResult();
                                }).orElse(new TurSESpellCheckResult()))
                .orElse(new TurSESpellCheckResult());
    }

    /**
     * Sanitizes a term before sending it to the {@code /tur_spell} handler. That
     * handler uses the default {@code lucene} query parser (no {@code defType}), so an
     * unescaped {@code :} in the term (e.g. {@code "remuneração: Planejamento"}) is
     * parsed as a {@code field:value} clause and Solr fails with "undefined field".
     * Quotes are stripped and the remaining Lucene syntax characters are escaped, while
     * whitespace is preserved so the spellchecker still analyzes each word.
     */
    private static String escapeSpellCheckTerm(String term) {
        if (StringUtils.isEmpty(term)) {
            return term;
        }
        return SPELL_CHECK_SPECIAL_CHARS.matcher(term.replace("\"", EMPTY)).replaceAll("\\\\$0");
    }

    public TurSEResult findById(TurSolrInstance turSolrInstance, TurSNSite turSNSite, String id,
            TurSNSiteSearchContext context) {
        SolrQuery query = new SolrQuery().setQuery(ID + ": \"" + id + "\"");
        return executeSolrQuery(turSolrInstance, query)
                .map(queryResponse -> queryResponse.getResults().stream().findFirst()
                        .map(solrDocument -> turSolrResultProcessor.createTurSEResult(
                                turSolrResultProcessor.getFieldExtMap(turSNSite),
                                turSolrResultProcessor.getRequiredFields(turSNSite), solrDocument,
                                turSolrResultProcessor.getHL(turSNSite,
                                        turSolrQueryBuilder.prepareQueryHL(turSNSite, query, context),
                                        queryResponse, solrDocument)))
                        .orElse(TurSEResult.builder().build()))
                .orElse(TurSEResult.builder().build());
    }

    private TurSESpellCheckResult prepareQueryAutoCorrection(
            TurSNSiteSearchContext turSNSiteSearchContext, TurSNSite turSNSite,
            TurSolrInstance turSolrInstance) {
        TurSESpellCheckResult turSESpellCheckResult = spellCheckTerm(turSolrInstance,
                turSNSiteSearchContext.getTurSEParameters().getQuery());
        if (TurSNUtils.isAutoCorrectionEnabled(turSNSiteSearchContext, turSNSite)) {
            turSESpellCheckResult.setUsingCorrected(true);
            if (TurSNUtils.hasCorrectedText(turSESpellCheckResult)) {
                turSNSiteSearchContext.setUri(
                        TurCommonsUtils.addOrReplaceParameter(turSNSiteSearchContext.getUri(),
                                QUERY, turSESpellCheckResult.getCorrectedText(), true));
            }
        } else {
            turSESpellCheckResult.setUsingCorrected(false);
        }
        return turSESpellCheckResult;
    }

    public Optional<TurSEResults> retrieveSolrFromSN(TurSolrInstance turSolrInstance,
            TurSNSiteSearchContext context) {
        return turSNSiteRepository.findByNameIgnoreCase(context.getSiteName()).map(turSNSite -> {
            TurSESpellCheckResult turSESpellCheckResult = prepareQueryAutoCorrection(context, turSNSite,
                    turSolrInstance);
            TurSEParameters turSEParameters = context.getTurSEParameters();
            SolrQuery query = turSolrQueryBuilder.prepareSolrQuery(context, turSNSite, turSEParameters,
                    turSESpellCheckResult);

            TurSolrQueryContext queryContext = TurSolrQueryContext.builder()
                    .query(query)
                    .turSEParameters(turSEParameters)
                    .mltFieldExtList(turSolrQueryBuilder.prepareQueryMLT(turSNSite, query))
                    .facetFieldExtList(turSolrQueryBuilder.prepareQueryFacet(turSNSite, query,
                            turSEParameters.getTurSNFilterParams(), turSEParameters))
                    .hlFieldExtList(turSolrQueryBuilder.prepareQueryHL(turSNSite, query, context))
                    .spellCheckResult(turSESpellCheckResult)
                    .queryToRenderFacet(false)
                    .locale(context.getLocale())
                    .build();

            return executeSolrQueryFromSN(turSolrInstance, turSNSite, queryContext);
        }).orElse(Optional.empty());
    }

    public Optional<TurSEResults> retrieveFacetSolrFromSN(TurSolrInstance turSolrInstance,
            TurSNSiteSearchContext context, String facetName) {
        return turSNSiteRepository.findByNameIgnoreCase(context.getSiteName()).map(turSNSite -> {
            TurSEParameters turSEParameters = context.getTurSEParameters();
            SolrQuery query = turSolrQueryBuilder.prepareSolrQuery(context, turSNSite, turSEParameters,
                    new TurSESpellCheckResult());

            TurSolrQueryContext queryContext = TurSolrQueryContext.builder()
                    .query(query)
                    .turSEParameters(turSEParameters)
                    .mltFieldExtList(Collections.emptyList())
                    .facetFieldExtList(turSolrQueryBuilder.prepareQueryFacetWithOneFacet(turSNSite, query,
                            turSEParameters.getTurSNFilterParams(), facetName))
                    .hlFieldExtList(Collections.emptyList())
                    .spellCheckResult(new TurSESpellCheckResult())
                    .queryToRenderFacet(true)
                    .locale(context.getLocale())
                    .build();

            return executeSolrQueryFromSN(turSolrInstance, turSNSite, queryContext);
        }).orElse(Optional.empty());
    }

    private Optional<TurSEResults> executeSolrQueryFromSN(TurSolrInstance turSolrInstance,
            TurSNSite turSNSite, TurSolrQueryContext queryContext) {
        SolrQuery query = queryContext.getQuery();
        boolean isQueryToRenderFacet = queryContext.isQueryToRenderFacet();

        if (!isQueryToRenderFacet && enabledWildcardAlways(turSNSite)
                && isNotQueryExpression(query)) {
            addAWildcardInQuery(query);
        }

        return executeSolrQuery(turSolrInstance, query)
                .map(queryResponse -> turSolrResultProcessor.getResults(turSolrInstance, turSNSite,
                        query, queryContext, getQueryResponseModified(turSolrInstance, turSNSite, query,
                                queryResponse, isQueryToRenderFacet),
                        turSolrQueryBuilder));
    }

    private static QueryResponse getQueryResponseModified(TurSolrInstance turSolrInstance,
            TurSNSite turSNSite, SolrQuery query, QueryResponse queryResponse,
            boolean isQueryToRenderFacet) {
        return whenNoResultsUseWildcard(turSNSite, query, queryResponse, isQueryToRenderFacet)
                ? executeSolrQuery(turSolrInstance, query).orElse(queryResponse)
                : queryResponse;
    }

    public static Optional<QueryResponse> executeSolrQuery(TurSolrInstance turSolrInstance, SolrQuery query) {
        String core = turSolrInstance.getCore();
        // A facet on a field that does not exist in this core's schema makes Solr reject the
        // WHOLE query (documents included). That happens on partially-indexed cores, where a
        // configured facet field was never created because no document ever carried it. Instead
        // of failing the entire search, strip the offending facet field and retry. The retry
        // budget is bounded by the number of facet fields, so it can never loop unbounded.
        int maxAttempts = 1 + Optional.ofNullable(query.getFacetFields()).map(f -> f.length).orElse(0);
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                QueryResponse response = turSolrInstance.getSolrClient().query(core, query);
                return Optional.ofNullable(response);
            } catch (SolrServerException | IOException e) {
                logSolrException(query, e, false);
                return Optional.empty();
            } catch (SolrException e) {
                // RemoteSolrException (server-side rejection, e.g. undefined field) is unchecked
                // and would otherwise escape the resilience wrapper as an unhandled exception.
                String removedFacet = removeUndefinedFacetField(query, e);
                if (removedFacet == null) {
                    // The undefined field is not a facet field we can strip (e.g. it is in q/fq/hl),
                    // so retrying would not help — log and give up as before.
                    logSolrException(query, e, true);
                    return Optional.empty();
                }
                log.warn("Skipping undefined facet field '{}' in Solr core '{}' and retrying query.",
                        removedFacet, core);
            }
        }
        return Optional.empty();
    }

    /**
     * When Solr rejects a query for an {@code undefined field}, removes that field from the
     * query's {@code facet.field} list so the caller can retry. Returns the removed field name,
     * or {@code null} when the undefined field is not a stripable facet field (the caller must
     * then stop retrying). Facet field entries may carry a leading Solr local-params block
     * (e.g. {@code {!ex=tag}catedras}); the bare field name is compared against Solr's message.
     */
    private static String removeUndefinedFacetField(SolrQuery query, SolrException e) {
        String field = parseUndefinedField(e.getMessage());
        if (field == null) {
            return null;
        }
        String[] facetFields = query.getFacetFields();
        if (facetFields == null) {
            return null;
        }
        boolean removed = false;
        for (String facetField : facetFields) {
            if (bareFacetField(facetField).equals(field)) {
                query.removeFacetField(facetField);
                removed = true;
            }
        }
        return removed ? field : null;
    }

    private static String parseUndefinedField(String message) {
        if (message == null) {
            return null;
        }
        Matcher matcher = UNDEFINED_FIELD.matcher(message);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String bareFacetField(String facetField) {
        // Strip a leading local-params block, e.g. "{!ex=tag}catedras" -> "catedras".
        int close = facetField.lastIndexOf('}');
        return close >= 0 ? facetField.substring(close + 1) : facetField;
    }

    private static void logSolrException(SolrQuery query, Exception e, boolean isRemote) {
        QueryRequest req = new QueryRequest(query);
        String msg = e.getMessage();
        if (isRemote && msg != null && msg.contains("undefined field")) {
            log.warn("Undefined field in Solr query [{}?{}]: {}", req.getPath(), query.toQueryString(), msg);
        } else {
            log.error("Error executing Solr query [{}?{}]: {}", req.getPath(), query.toQueryString(), msg, e);
        }
    }

    public static void addAWildcardInQuery(SolrQuery query) {
        query.setQuery(query.getQuery().trim() + "*");
    }

    private static boolean whenNoResultsUseWildcard(TurSNSite turSNSite, SolrQuery query,
            QueryResponse queryResponse, boolean isQueryToRenderFacet) {
        if (!isQueryToRenderFacet && !enabledWildcardAlways(turSNSite)
                && enabledWildcardNoResults(turSNSite) && isNotQueryExpression(query)
                && noResultGroups(queryResponse) && noResults(queryResponse)) {
            addAWildcardInQuery(query);
            return true;
        } else {
            return false;
        }
    }

    public static boolean enabledWildcardNoResults(TurSNSite turSNSite) {
        return turSNSite.getWildcardNoResults() != null && turSNSite.getWildcardNoResults() == 1;
    }

    private static boolean enabledWildcardAlways(TurSNSite turSNSite) {
        return turSNSite.getWildcardAlways() != null && turSNSite.getWildcardAlways() == 1;
    }

    public static boolean isNotQueryExpression(SolrQuery query) {
        return !query.getQuery().endsWith("*") || !query.getQuery().endsWith("\"")
                || !query.getQuery().endsWith("]") || !query.getQuery().endsWith(")");
    }

    private static boolean noResults(QueryResponse queryResponse) {
        return queryResponse.getResults() == null
                || (queryResponse.getResults() != null && queryResponse.getResults().isEmpty());
    }

    private static boolean noResultGroups(QueryResponse queryResponse) {
        GroupResponse groupResponse = queryResponse.getGroupResponse();
        return groupResponse == null || groupResponse.getValues().isEmpty()
                || groupResponse.getValues().size() == 1
                        && groupResponse.getValues().getFirst().getValues().isEmpty();
    }

    private static void filterQueryRequest(TurSEParameters turSEParameters, SolrQuery query) {
        Optional.ofNullable(turSEParameters.getTurSNFilterParams().getDefaultValues())
                .ifPresent(filterQueries -> {
                    String[] filterQueryArr = new String[filterQueries.size()];
                    query.setFilterQueries(filterQueries.toArray(filterQueryArr));
                });
    }

    public TurSEResults retrieveSolr(TurSolrInstance turSolrInstance,
            TurSEParameters turSEParameters, String defaultSortField) {
        SolrQuery query = new SolrQuery().setQuery(turSEParameters.getQuery())
                .setRows(turSEParameters.getRows())
                .setStart(firstRowPositionFromCurrentPage(turSEParameters));
        Optional.ofNullable(turSEParameters.getSort()).ifPresent(sort -> {
            if (sort.equalsIgnoreCase(TurSolrConstants.NEWEST)) {
                query.setSort(defaultSortField, ORDER.desc);
            } else if (sort.equalsIgnoreCase(TurSolrConstants.OLDEST)) {
                query.setSort(defaultSortField, ORDER.asc);
            }
        });
        filterQueryRequest(turSEParameters, query);
        return executeSolrQuery(turSolrInstance, query).map(queryResponse -> {
            TurSEResults turSEResults = TurSEResults.builder()
                    .spellCheck(spellCheckTerm(turSolrInstance, turSEParameters.getQuery()))
                    .results(queryResponse.getResults().stream()
                            .map(TurSolr::createTurSEResultFromDocument).toList())
                    .build();
            turSolrResultProcessor.turSEResultsParameters(turSEParameters, query, turSEResults, queryResponse);
            return turSEResults;
        }).orElse(TurSEResults.builder().build());
    }

    public static int firstRowPositionFromCurrentPage(TurSEParameters turSEParameters) {
        // Clamp to 0: a client-supplied page <= 0 would otherwise yield a negative Solr
        // 'start', which Solr rejects with "'start' parameter cannot be negative".
        return Math.max(0, (turSEParameters.getCurrentPage() - 1) * turSEParameters.getRows());
    }

    public static TurSEResult createTurSEResultFromDocument(SolrDocument document) {
        Map<String, Object> fields = new HashMap<>();
        for (String attribute : document.getFieldNames()) {
            fields.put(attribute, document.getFieldValue(attribute));
        }
        return TurSEResult.builder().fields(fields).build();
    }

    public boolean commit(TurSolrInstance turSolrInstance) {
        if (!isCommitEnabled) {
            return true;
        }
        try {
            turSolrInstance.getSolrClient().commit(turSolrInstance.getCore());
        } catch (SolrServerException | IOException e) {
            log.error(e.getMessage());
        }
        return true;
    }
}
