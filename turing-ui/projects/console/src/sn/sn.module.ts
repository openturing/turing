import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MomentModule } from 'ngx-moment';
import { TurSNSiteService } from './service/sn-site.service';
import { TurSNSiteListPageComponent } from './component/site/sn-site-list-page.component';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { TurSNRoutingModule } from './sn-routing.module';
import { TurCommonsModule } from '../commons/commons.module';
import { OcticonsModule } from 'ngx-octicons';
import { RouterModule } from '@angular/router';
import { TurSNRootPageComponent } from './component/root/sn-root-page.component';
import { TurSNSitePageComponent } from './component/site/sn-site-page.component';
import { TurSNSiteDetailPageComponent } from './component/site/sn-site-detail-page.component';
import { TurSNSiteUIPageComponent } from './component/site/sn-site-ui-page.component';
import { TurLocaleService } from '../locale/service/locale.service';
import { TurSEInstanceService } from '../se/service/se-instance.service';
import { TurNLPInstanceService } from '../nlp/service/nlp-instance.service';
import { TurSNFieldTypeService } from '../sn/service/sn-field-type.service';
import { TurSNSiteSpotlightService } from '../sn/service/sn-site-spotlight.service';
import { TurSNSiteFieldRootPageComponent } from './component/site/field/sn-site-field-root-page.component';
import { TurSNSiteFieldListPageComponent } from './component/site/field/sn-site-field-list-page.component';
import { TurSNSiteFieldPageComponent } from './component/site/field/sn-site-field-page.component';
import { TurSNSiteSpotlightRootPageComponent } from './component/site/spotlight/sn-site-spotlight-root-page.component';
import { TurSNSiteSpotlightListPageComponent } from './component/site/spotlight/sn-site-spotlight-list-page.component';
import { TurSNSiteSpotlightPageComponent } from './component/site/spotlight/sn-site-spotlight-page.component';
import { TurSNSiteLocaleService } from './service/sn-site-locale.service';
import { TurSNSiteLocalePageComponent } from './component/site/locale/sn-site-locale-page.component';
import { TurSNSiteLocaleRootPageComponent } from './component/site/locale/sn-site-locale-root-page.component';
import { TurSNSiteLocaleListPageComponent } from './component/site/locale/sn-site-locale-list-page.component';
import { TurNLPVendorService } from '../nlp/service/nlp-vendor.service';

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
    TurSNSiteLocalePageComponent
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
    TurSNSiteLocaleService
  ],
  schemas: [ CUSTOM_ELEMENTS_SCHEMA ]
})
export class TurSNModule { }
