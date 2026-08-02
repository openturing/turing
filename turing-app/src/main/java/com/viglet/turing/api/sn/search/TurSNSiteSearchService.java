package com.viglet.turing.api.sn.search;

import java.net.URI;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.apache.commons.lang3.LocaleUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.viglet.turing.commons.se.TurSEParameters;
import com.viglet.turing.commons.sn.TurSNConfig;
import com.viglet.turing.commons.sn.bean.TurSNSearchLatestRequestBean;
import com.viglet.turing.commons.sn.bean.TurSNSearchParams;
import com.viglet.turing.commons.sn.bean.TurSNSearchRequest;
import com.viglet.turing.commons.sn.bean.TurSNSiteLocaleBean;
import com.viglet.turing.commons.sn.bean.TurSNSitePostParamsBean;
import com.viglet.turing.commons.sn.bean.TurSNSiteSearchBean;
import com.viglet.turing.commons.sn.search.TurSNFilterQueryOperator;
import com.viglet.turing.commons.sn.search.TurSNSearchCacheKey;
import com.viglet.turing.commons.sn.search.TurSNSiteSearchContext;
import com.viglet.turing.persistence.model.sn.locale.TurSNSiteLocale;
import com.viglet.turing.sn.TurSNSearchProcess;
import com.viglet.turing.sn.TurSNUtils;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshot;
import com.viglet.turing.sn.snapshot.TurSNSiteSearchSnapshotService;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class TurSNSiteSearchService {

    @Value("${turing.search.cache.enabled:false}")
    private boolean searchCacheEnabled;
    private final TurSNSearchProcess turSNSearchProcess;
    private final TurSNSiteSearchCachedAPI turSNSiteSearchCachedAPI;
    private final TurSNSiteSearchSnapshotService snapshotService;

    public TurSNSiteSearchService(TurSNSearchProcess turSNSearchProcess,
            TurSNSiteSearchCachedAPI turSNSiteSearchCachedAPI,
            TurSNSiteSearchSnapshotService snapshotService) {
        this.turSNSearchProcess = turSNSearchProcess;
        this.turSNSiteSearchCachedAPI = turSNSiteSearchCachedAPI;
        this.snapshotService = snapshotService;
    }

    public Locale convertToLocale(String localeRequest) {
        return LocaleUtils.toLocale(localeRequest);
    }

    public TurSNSiteSearchContext getTurSNSiteSearchContext(
            TurSNSearchParams turSNSearchParams, HttpServletRequest request, String siteName) {
        TurSNConfig turSNConfig = getTurSNConfig(siteName);
        return TurSNUtils.getTurSNSiteSearchContext(turSNConfig,
                siteName, turSNSearchParams, request);
    }

    public ResponseEntity<TurSNSiteSearchBean> executeGetSearch(
            TurSNSearchParams turSNSearchParams,
            HttpServletRequest request,
            String siteName) {

        TurSNSiteSearchContext searchContext = getTurSNSiteSearchContext(turSNSearchParams, request, siteName);
        return searchCacheEnabled
                ? executeSearchWithCache(searchContext)
                : executeSearch(searchContext);
    }

    public <T> ResponseEntity<T> notFoundResponse() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    public ResponseEntity<TurSNSiteSearchBean> executeSearch(TurSNSiteSearchContext turSNSiteSearchContext) {
        return new ResponseEntity<>(
                turSNSearchProcess.search(turSNSiteSearchContext), HttpStatus.OK);
    }

    /**
     * T858 / §LXV.3 — the cache key is derived from the context's effective
     * parameters, so it cannot depend on how the request arrived and no caller
     * has to remember to supply one.
     */
    public ResponseEntity<TurSNSiteSearchBean> executeSearchWithCache(
            TurSNSiteSearchContext turSNSiteSearchContext) {
        return new ResponseEntity<>(
                turSNSiteSearchCachedAPI.searchCached(
                        TurSNSearchCacheKey.of(turSNSiteSearchContext),
                        turSNSiteSearchContext),
                HttpStatus.OK);
    }

    public ResponseEntity<TurSNSiteSearchBean> executePostSearch(
            TurSNSearchParams turSNSearchParams,
            TurSNSitePostParamsBean turSNSitePostParamsBean,
            HttpServletRequest request,
            String siteName) {
        if (StringUtils.isNotBlank(turSNSitePostParamsBean.getLocale())) {
            turSNSearchParams.setLocale(
                    convertToLocale(turSNSitePostParamsBean.getLocale()));
        }

        turSNSitePostParamsBean.setTargetingRules(
                turSNSearchProcess.requestTargetingRules(turSNSitePostParamsBean.getTargetingRules()));
        TurSNSiteSearchContext searchContext = TurSNUtils.getTurSNSiteSearchContext(
                getTurSNConfig(siteName), siteName, turSNSearchParams,
                turSNSitePostParamsBean, request);
        if (searchCacheEnabled) {
            return executeSearchWithCache(searchContext);
        } else {
            return executeSearch(searchContext);
        }
    }

    /**
     * T707 / §XLV — executes the public, anonymous-capable POST search. The JSON
     * body replaces the query string entirely, so no filter value ever has to
     * survive a query-string round-trip; the canonical GET URI is rebuilt only so
     * the legacy link-bearing response keeps emitting deep-linkable hrefs.
     *
     * @since 2026.3.4
     */
    public ResponseEntity<TurSNSiteSearchBean> executePublicPostSearch(
            TurSNSearchRequest searchRequest,
            TurSNSearchParams turSNSearchParams,
            HttpServletRequest request,
            String siteName) {
        TurSNSiteSearchContext searchContext =
                publicPostSearchContext(searchRequest, turSNSearchParams, request, siteName);
        return searchCacheEnabled
                ? executeSearchWithCache(searchContext)
                : executeSearch(searchContext);
    }

    /**
     * T712 / §XLV — the {@code /list} (select-list) counterpart of
     * {@link #executePublicPostSearch}, so the whole public read surface can be
     * driven from the same JSON body. The list projection has no links to render,
     * so there is nothing to negotiate: the shape is the same {@code List<Object>}
     * the GET mapping returns.
     *
     * @since 2026.3.4
     */
    public ResponseEntity<List<Object>> executePublicPostSearchList(
            TurSNSearchRequest searchRequest,
            TurSNSearchParams turSNSearchParams,
            HttpServletRequest request,
            String siteName) {
        return new ResponseEntity<>(turSNSearchProcess.searchList(
                publicPostSearchContext(searchRequest, turSNSearchParams, request, siteName)),
                HttpStatus.OK);
    }

    /**
     * Builds the search context for a public POST call. The canonical GET URI is
     * rebuilt only so the legacy link-bearing response keeps emitting
     * deep-linkable hrefs; the JSON body itself never goes through a query string.
     *
     * @since 2026.3.4
     */
    public TurSNSiteSearchContext publicPostSearchContext(
            TurSNSearchRequest searchRequest,
            TurSNSearchParams turSNSearchParams,
            HttpServletRequest request,
            String siteName) {
        TurSNSitePostParamsBean postParams = TurSNSearchRequestAdapter.toPostParams(searchRequest);
        postParams.setTargetingRules(
                turSNSearchProcess.requestTargetingRules(postParams.getTargetingRules()));
        URI canonicalUri = TurSNSearchRequestAdapter.canonicalUri(
                TurSNUtils.requestToURI(request), searchRequest, turSNSearchParams.getLocale());
        return new TurSNSiteSearchContext(siteName, getTurSNConfig(siteName),
                new TurSEParameters(turSNSearchParams, postParams),
                turSNSearchParams.getLocale(), canonicalUri, postParams);
    }

    public TurSNConfig getTurSNConfig(String siteName) {
        TurSNConfig turSNConfig = new TurSNConfig();
        turSNConfig.setHlEnabled(snapshotService.getSnapshot(siteName, null)
                .map(snap -> TurSNUtils.isTrue(snap.site().getHl()) && snap.hasHlFields())
                .orElse(false));
        return turSNConfig;
    }

    public boolean hasSiteHLFields(String siteName) {
        return snapshotService.getSnapshot(siteName, null)
                .map(TurSNSiteSearchSnapshot::hasHlFields)
                .orElse(false);
    }

    /**
     * Returns true when the site exists. Uses the snapshot so the lookup costs
     * one cache hit instead of a DB roundtrip.
     */
    public boolean siteExists(String siteName) {
        return snapshotService.getSnapshot(siteName, null).isPresent();
    }

    public String isLatestImpersonate(
            Optional<TurSNSearchLatestRequestBean> turSNSearchLatestRequestBean,
            Principal principal) {
        if (turSNSearchLatestRequestBean.isPresent()
                && turSNSearchLatestRequestBean.get().getUserId() != null) {
            return turSNSearchLatestRequestBean.get().getUserId();
        } else {
            return principal.getName();
        }
    }

    public static List<TurSNSiteLocaleBean> getTurSNSiteLocaleBeans() {
        return new ArrayList<>();
    }

    public Locale determineLocale(TurSNSitePostParamsBean turSNSitePostParamsBean, Locale locale) {
        return StringUtils.isNotBlank(turSNSitePostParamsBean.getLocale())
                ? convertToLocale(turSNSitePostParamsBean.getLocale())
                : locale;
    }

    public void setSearchParams(TurSNSearchParams turSNSearchParams, List<String> filterQueriesDefault,
            List<String> filterQueriesAnd, List<String> filterQueriesOr,
            TurSNFilterQueryOperator fqOperator, TurSNFilterQueryOperator fqItemOperator,
            Locale locale) {
        if (filterQueriesDefault != null)
            turSNSearchParams.setFq(filterQueriesDefault);
        if (filterQueriesAnd != null)
            turSNSearchParams.setFqAnd(filterQueriesAnd);
        if (filterQueriesOr != null)
            turSNSearchParams.setFqOr(filterQueriesOr);
        if (fqOperator != null)
            turSNSearchParams.setFqOp(fqOperator);
        if (fqItemOperator != null)
            turSNSearchParams.setFqiOp(fqItemOperator);
        if (locale != null)
            turSNSearchParams.setLocale(locale);
    }

    public Locale resolveDefaultLocale(String siteName) {
        return snapshotService.getSnapshot(siteName, null)
                .flatMap(snap -> snap.allLocales().stream().findFirst())
                .map(TurSNSiteLocale::getLanguage)
                .orElse(null);
    }

}
