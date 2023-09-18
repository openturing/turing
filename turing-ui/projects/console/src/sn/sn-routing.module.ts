import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '../app/_helpers';
import { TurSNSitePageComponent } from './component/site/sn-site-page.component';
import { TurSNSiteListPageComponent } from './component/site/sn-site-list-page.component';
import { TurSNRootPageComponent } from './component/root/sn-root-page.component';
import { TurSNSiteDetailPageComponent } from './component/site/sn-site-detail-page.component';
import { TurSNSiteUIPageComponent } from './component/site/sn-site-ui-page.component';
import { TurSNSiteFieldRootPageComponent } from './component/site/field/sn-site-field-root-page.component';
import { TurSNSiteFieldListPageComponent } from './component/site/field/sn-site-field-list-page.component';
import { TurSNSiteFieldPageComponent } from './component/site/field/sn-site-field-page.component';
import { TurSNSiteSpotlightRootPageComponent } from './component/site/spotlight/sn-site-spotlight-root-page.component';
import { TurSNSiteSpotlightListPageComponent } from './component/site/spotlight/sn-site-spotlight-list-page.component';
import { TurSNSiteSpotlightPageComponent } from './component/site/spotlight/sn-site-spotlight-page.component';
import { TurSNSiteLocaleRootPageComponent } from './component/site/locale/sn-site-locale-root-page.component';
import { TurSNSiteLocaleListPageComponent } from './component/site/locale/sn-site-locale-list-page.component';
import { TurSNSiteLocalePageComponent } from './component/site/locale/sn-site-locale-page.component';
import { TurSNSiteMergeRootPageComponent } from './component/site/merge/sn-site-merge-root-page.component';
import { TurSNSiteMergeListPageComponent } from './component/site/merge/sn-site-merge-list-page.component';
import { TurSNSiteMergePageComponent } from './component/site/merge/sn-site-merge-page.component';
import { TurSNSiteMetricsRootPageComponent } from './component/site/metrics/sn-site-metrics-root-page.component';
import { TurSNSiteMetricsTopTermsPageComponent } from './component/site/metrics/sn-site-metrics-top-terms-page.component';
import { TurSNSiteMetricsTopTermsRootPageComponent } from './component/site/metrics/sn-site-metrics-top-terms-root-page.component';
import {
  TurSNRankingExpressionRootPageComponent
} from "./component/site/ranking/sn-site-ranking-expression-root-page.component";
import {
  TurSNRankingExpressionListPageComponent
} from "./component/site/ranking/sn-ranking-expression-list-page.component";
import {TurSNRankingExpressionPageComponent} from "./component/site/ranking/sn-ranking-expression-page.component";

const routes: Routes = [
  {
    path: '', component: TurSNRootPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'site', component: TurSNSiteListPageComponent, canActivate: [AuthGuard] },
      {
        path: 'site/:id', component: TurSNSitePageComponent, canActivate: [AuthGuard],
        children: [
          {
            path: 'metrics', component: TurSNSiteMetricsRootPageComponent, canActivate: [AuthGuard],
            children: [
              {
                path: 'top-terms', component: TurSNSiteMetricsTopTermsRootPageComponent, canActivate: [AuthGuard],
                children: [
                  { path: ':period', component: TurSNSiteMetricsTopTermsPageComponent, canActivate: [AuthGuard] },
                  { path: '', redirectTo: 'this-month', pathMatch: 'full' }
                ]
              },
              { path: '', redirectTo: 'top-terms', pathMatch: 'full' }
            ]
          },
          {
            path: 'ranking-expression', component: TurSNRankingExpressionRootPageComponent, canActivate: [AuthGuard],
            children: [
              { path: 'list', component: TurSNRankingExpressionListPageComponent, canActivate: [AuthGuard] },
              { path: ':rankingExpressionId', component: TurSNRankingExpressionPageComponent, canActivate: [AuthGuard] },
              { path: '', redirectTo: 'list', pathMatch: 'full' }
            ]
          },
          {
            path: 'spotlight', component: TurSNSiteSpotlightRootPageComponent, canActivate: [AuthGuard],
            children: [
              { path: 'list', component: TurSNSiteSpotlightListPageComponent, canActivate: [AuthGuard] },
              { path: ':spotlightId', component: TurSNSiteSpotlightPageComponent, canActivate: [AuthGuard] },
              { path: '', redirectTo: 'list', pathMatch: 'full' }
            ]
          },
          {
            path: 'merge', component: TurSNSiteMergeRootPageComponent, canActivate: [AuthGuard],
            children: [
              { path: 'list', component: TurSNSiteMergeListPageComponent, canActivate: [AuthGuard] },
              { path: ':mergeId', component: TurSNSiteMergePageComponent, canActivate: [AuthGuard] },
              { path: '', redirectTo: 'list', pathMatch: 'full' }
            ]
          },
          {
            path: 'locale', component: TurSNSiteLocaleRootPageComponent, canActivate: [AuthGuard],
            children: [
              { path: 'list', component: TurSNSiteLocaleListPageComponent, canActivate: [AuthGuard] },
              { path: ':localeId', component: TurSNSiteLocalePageComponent, canActivate: [AuthGuard] },
              { path: '', redirectTo: 'list', pathMatch: 'full' }
            ]
          },
          { path: 'detail', component: TurSNSiteDetailPageComponent, canActivate: [AuthGuard] },
          {
            path: 'field', component: TurSNSiteFieldRootPageComponent, canActivate: [AuthGuard],
            children: [
              { path: 'list', component: TurSNSiteFieldListPageComponent, canActivate: [AuthGuard] },
              { path: ':fieldId', component: TurSNSiteFieldPageComponent, canActivate: [AuthGuard] },
              { path: '', redirectTo: 'list', pathMatch: 'full' }
            ]
          },
          { path: 'ui', component: TurSNSiteUIPageComponent, canActivate: [AuthGuard] },
          { path: '', redirectTo: 'detail', pathMatch: 'full' }
        ]
      },
      { path: '', redirectTo: '/sn/site', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurSNRoutingModule { }
