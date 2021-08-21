import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurSNSiteService } from './service/sn-site.service';
import { TurSNSiteListPageComponent } from './component/site/sn-site-list-page.component';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { TurSNRoutingModule } from './sn-routing.module';
import { ShioCommonsModule } from 'src/commons/shio-commons.module';
import { OcticonsModule } from 'ngx-octicons';
import { RouterModule } from '@angular/router';
import { TurSNRootPageComponent } from './component/root/sn-root-page.component';
import { TurSNSitePageComponent } from './component/site/sn-site-page.component';
import { TurSNSiteDetailPageComponent } from './component/site/sn-site-detail-page.component';
import { TurSNSiteUIPageComponent } from './component/site/sn-site-ui-page.component';
import { TurLocaleService } from '../locale/service/locale.service';
import { TurSEInstanceService } from 'src/se/service/se-instance.service';
import { TurNLPInstanceService } from 'src/nlp/service/nlp-instance.service';
import { TurSNSiteFieldRootPageComponent } from './component/site/field/sn-site-field-root-page.component';
import { TurSNSiteFieldListPageComponent } from './component/site/field/sn-site-field-list-page.component';
import { TurSNSiteFieldPageComponent } from './component/site/field/sn-site-field-page.component';
@NgModule({
  declarations: [
    TurSNRootPageComponent,
    TurSNSitePageComponent,
    TurSNSiteDetailPageComponent,
    TurSNSiteUIPageComponent,
    TurSNSiteFieldRootPageComponent,
    TurSNSiteFieldListPageComponent,
    TurSNSiteFieldPageComponent,
    TurSNSiteListPageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    OcticonsModule,
    TurSNRoutingModule,
    ShioCommonsModule,
    RouterModule
  ],
  providers: [
    TurSNSiteService,
    TurSEInstanceService,
    TurNLPInstanceService,
    TurLocaleService
  ]
})
export class TurSNModule { }
