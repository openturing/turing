import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurSNSiteService } from './service/sn-site.service';
import { TurSNSiteListPageComponent } from './component/site/sn-site-list-page.component';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { AceEditorModule } from 'ace-editor-ng9';
import { TurSNRoutingModule } from './sn-routing.module';
import { ShioCommonsModule } from 'src/commons/shio-commons.module';
import { OcticonsModule } from 'ngx-octicons';
import { RouterModule } from '@angular/router';
import { TurSNRootPageComponent } from './component/root/sn-root-page.component';
import { TurSNSitePageComponent } from './component/site/sn-site-page.component';
import { TurLocaleService } from '../locale/service/locale.service';
@NgModule({
  declarations: [
    TurSNRootPageComponent,
    TurSNSitePageComponent,
    TurSNSiteListPageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    AceEditorModule,
    OcticonsModule,
    TurSNRoutingModule,
    ShioCommonsModule,
    RouterModule
  ],
  providers: [
    TurSNSiteService,
    TurLocaleService
  ]
})
export class TurSNModule { }
