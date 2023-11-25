import {CUSTOM_ELEMENTS_SCHEMA, NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {MomentModule} from 'ngx-moment';
import {TurSNSiteService} from './service/sn-site.service';
import {TurSNSiteListPageComponent} from './component/site/sn-site-list-page.component';
import {ReactiveFormsModule, FormsModule} from '@angular/forms';
import {TurSNRoutingModule} from './sn-routing.module';
import {TurCommonsModule} from '../commons/commons.module';
import {OcticonsModule} from 'angular-octicons';
import {RouterModule} from '@angular/router';
import {TurSNRootPageComponent} from './component/root/sn-root-page.component';
import {TurSNSitePageComponent} from './component/site/sn-site-page.component';
import {TurSNSiteDetailPageComponent} from './component/site/sn-site-detail-page.component';
import {TurSNSiteUIPageComponent} from './component/site/sn-site-ui-page.component';
import {TurLocaleService} from '../locale/service/locale.service';
import {TurSEInstanceService} from '../se/service/se-instance.service';
import {TurNLPInstanceService} from '../nlp/service/nlp-instance.service';
import {TurSNFieldTypeService} from './service/sn-field-type.service';
import {TurSNSiteSpotlightService} from './service/sn-site-spotlight.service';
import {TurSNSiteFieldRootPageComponent} from './component/site/field/sn-site-field-root-page.component';
import {TurSNSiteFieldListPageComponent} from './component/site/field/sn-site-field-list-page.component';
import {TurSNSiteFieldPageComponent} from './component/site/field/sn-site-field-page.component';
import {TurSNSiteSpotlightRootPageComponent} from './component/site/spotlight/sn-site-spotlight-root-page.component';
import {TurSNSiteSpotlightListPageComponent} from './component/site/spotlight/sn-site-spotlight-list-page.component';
import {TurSNSiteSpotlightPageComponent} from './component/site/spotlight/sn-site-spotlight-page.component';
import {TurSNSiteLocaleService} from './service/sn-site-locale.service';
import {TurSNSiteLocalePageComponent} from './component/site/locale/sn-site-locale-page.component';
import {TurSNSiteLocaleRootPageComponent} from './component/site/locale/sn-site-locale-root-page.component';
import {TurSNSiteLocaleListPageComponent} from './component/site/locale/sn-site-locale-list-page.component';
import {TurNLPVendorService} from '../nlp/service/nlp-vendor.service';
import {TurSNSiteMergeListPageComponent} from './component/site/merge/sn-site-merge-list-page.component';
import {TurSNSiteMergePageComponent} from './component/site/merge/sn-site-merge-page.component';
import {TurSNSiteMergeRootPageComponent} from './component/site/merge/sn-site-merge-root-page.component';
import {TurSNSiteMergeService} from './service/sn-site-merge.service';
import {TurSNSiteMetricsRootPageComponent} from './component/site/metrics/sn-site-metrics-root-page.component';
import {TurSNSiteMetricsTopTermsPageComponent} from './component/site/metrics/sn-site-metrics-top-terms-page.component';
import {TurSNSiteMetricsService} from './service/sn-site-metrics.service';
import {
  TurSNSiteMetricsTopTermsRootPageComponent
} from './component/site/metrics/sn-site-metrics-top-terms-root-page.component';
import {
  TurSNRankingExpressionRootPageComponent
} from "./component/site/ranking/sn-site-ranking-expression-root-page.component";
import {
  TurSNRankingExpressionListPageComponent
} from "./component/site/ranking/sn-ranking-expression-list-page.component";
import {TurSNRankingExpressionService} from "./service/sn-ranking-expression.service";
import {TurSNRankingExpressionPageComponent} from "./component/site/ranking/sn-ranking-expression-page.component";
import {TurSNSearchService} from "../../../sn/src/search/service/sn-search.service";

@NgModule({
  declarations: [
    TurSNRootPageComponent,
    TurSNSitePageComponent,
    TurSNSiteDetailPageComponent,
    TurSNSiteUIPageComponent,
    TurSNSiteFieldRootPageComponent,
    TurSNSiteFieldListPageComponent,
    TurSNSiteFieldPageComponent,
    TurSNSiteListPageComponent,
    TurSNSiteSpotlightRootPageComponent,
    TurSNSiteSpotlightListPageComponent,
    TurSNSiteSpotlightPageComponent,
    TurSNSiteLocaleRootPageComponent,
    TurSNSiteLocaleListPageComponent,
    TurSNSiteLocalePageComponent,
    TurSNSiteMergeRootPageComponent,
    TurSNSiteMergeListPageComponent,
    TurSNSiteMergePageComponent,
    TurSNSiteMetricsRootPageComponent,
    TurSNSiteMetricsTopTermsRootPageComponent,
    TurSNSiteMetricsTopTermsPageComponent,
    TurSNRankingExpressionRootPageComponent,
    TurSNRankingExpressionListPageComponent,
    TurSNRankingExpressionPageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    OcticonsModule,
    TurSNRoutingModule,
    TurCommonsModule,
    RouterModule,
    MomentModule
  ],
  providers: [
    TurSNSiteService,
    TurSEInstanceService,
    TurNLPInstanceService,
    TurNLPVendorService,
    TurLocaleService,
    TurSNFieldTypeService,
    TurSNSiteSpotlightService,
    TurSNSiteLocaleService,
    TurSNSiteMergeService,
    TurSNSiteMetricsService,
    TurSNRankingExpressionService,
    TurSNSearchService
  ],
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class TurSNModule {
}
