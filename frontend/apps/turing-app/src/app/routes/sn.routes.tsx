import { lazy } from "react"
import { Navigate, Route, useParams } from "react-router-dom"
import { ROUTES } from "../routes.const"

// Heaviest console area — every form page (custom facet, multi-language) is
// large and pulls in CodeMirror / form libraries. Lazy loading keeps the
// initial bundle out of these chunks until the user navigates here.
const SNSiteBehaviorPage = lazy(() => import("../console/sn/behavior/sn.site.behavior.page"))
const SNSiteCustomSortListPage = lazy(() => import("../console/sn/custom-sort/sn.site.custom.sort.list.page"))
const SNSiteCustomSortPage = lazy(() => import("../console/sn/custom-sort/sn.site.custom.sort.page"))
const SNSiteSearchRuleListPage = lazy(() => import("../console/sn/search-rules/sn.site.search.rule.list.page"))
const SNSiteSearchRulePage = lazy(() => import("../console/sn/search-rules/sn.site.search.rule.page"))
const SNSiteDetailPage = lazy(() => import("../console/sn/detail/sn.site.detail.page"))
const SNSiteCustomFacetPage = lazy(() => import("../console/sn/facet/sn.site.custom.facet.page"))
const SNSiteCustomFacetItemPage = lazy(() => import("../console/sn/facet/sn.site.custom.facet.item.page"))
const SNSiteFacetListPage = lazy(() => import("../console/sn/facet/sn.site.facet.list.page"))
const SNSiteFacetedFieldPage = lazy(() => import("../console/sn/facet/sn.site.faceted.field.page"))
const SNSiteFieldListPage = lazy(() => import("../console/sn/fields/sn.site.field.list.page"))
const SNSiteFieldPage = lazy(() => import("../console/sn/fields/sn.site.field.page"))
const SNSiteGenAIPage = lazy(() => import("../console/sn/genai/sn.site.genai.page"))
const SNSiteMergeProvidersListPage = lazy(() => import("../console/sn/merge-providers/sn.site.merge.providers.list.page"))
const SNSiteMergeProvidersPage = lazy(() => import("../console/sn/merge-providers/sn.site.merge.providers.page"))
const SNSiteMultiLanguageListPage = lazy(() => import("../console/sn/multi-language/sn.site.multi.language.list.page"))
const SNSiteMultiLanguagePage = lazy(() => import("../console/sn/multi-language/sn.site.multi.language.page"))
const SNSiteResultRankingListPage = lazy(() => import("../console/sn/result-ranking/sn.site.result.ranking.list.page"))
const SNSiteResultRankingPage = lazy(() => import("../console/sn/result-ranking/sn.site.result.ranking.page"))
const SNSiteListPage = lazy(() => import("../console/sn/sn.site.list.page"))
const SNSitePage = lazy(() => import("../console/sn/sn.site.page"))
const SNSiteRootPage = lazy(() => import("../console/sn/sn.site.root.page"))
const SNSiteSpotlightListPage = lazy(() => import("../console/sn/spotlight/sn.site.spotlight.list.page"))
const SNSiteSpotlightPage = lazy(() => import("../console/sn/spotlight/sn.site.spotlight.page"))
const SNSiteInsightsPage = lazy(() => import("../console/sn/insights/sn.site.insights.page"))
const SNSiteTopSearchTermsPage = lazy(() => import("../console/sn/top-search-terms/sn.site.top.search.terms.page"))

function RedirectToSNDetail() {
    const { id } = useParams();
    return <Navigate to={`${ROUTES.SN_INSTANCE}/${id}/detail`} replace />;
}

export const SNRoutes = (
    <Route path={ROUTES.SN_ROOT} element={<SNSiteRootPage />}>
        <Route index element={<Navigate to={ROUTES.SN_INSTANCE} replace />} />
        <Route path={ROUTES.SN_INSTANCE} element={<SNSiteListPage />} />
        <Route path={`${ROUTES.SN_INSTANCE}/:id`} element={<SNSitePage />}>
            <Route index element={<RedirectToSNDetail />} />
            <Route path={'insights'} element={<SNSiteInsightsPage />} />
            <Route path={'detail'} element={<SNSiteDetailPage />} />
            <Route path={'locale'} element={<SNSiteMultiLanguageListPage />} />
            <Route path={'locale/:localeId'} element={<SNSiteMultiLanguagePage />} />
            <Route path={'field'} element={<SNSiteFieldListPage />} />
            <Route path={'field/:fieldId'} element={<SNSiteFieldPage />} />
            <Route path={'behavior'} element={<SNSiteBehaviorPage />} />
            <Route path={'facet'} element={<SNSiteFacetListPage />} />
            <Route path={'facet/custom/:customFacetId'} element={<SNSiteCustomFacetPage />} />
            <Route path={'facet/custom/:customFacetId/item/:itemIndex'} element={<SNSiteCustomFacetItemPage />} />
            <Route path={'facet/field/:facetedFieldId'} element={<SNSiteFacetedFieldPage />} />
            <Route path={'custom-sort'} element={<SNSiteCustomSortListPage />} />
            <Route path={'custom-sort/:customSortId'} element={<SNSiteCustomSortPage />} />
            <Route path={'search-rule'} element={<SNSiteSearchRuleListPage />} />
            <Route path={'search-rule/:searchRuleId'} element={<SNSiteSearchRulePage />} />
            <Route path={'ai'} element={<SNSiteGenAIPage />} />
            <Route path={'result-ranking'} element={<SNSiteResultRankingListPage />} />
            <Route path={'merge-providers'} element={<SNSiteMergeProvidersListPage />} />
            <Route path={'spotlight'} element={<SNSiteSpotlightListPage />} />
            <Route path={'spotlight/:spotlightId'} element={<SNSiteSpotlightPage />} />
            <Route path={'top-terms/:period?'} element={<SNSiteTopSearchTermsPage />} />
            <Route path={'result-ranking/:resultRankingId'} element={<SNSiteResultRankingPage />} />
            <Route path={'merge-providers/:mergeProviderId'} element={<SNSiteMergeProvidersPage />} />
        </Route>
    </Route>
)
