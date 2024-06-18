import {CUSTOM_ELEMENTS_SCHEMA, NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TurIntegrationInstanceService} from './service/integration-instance.service';
import {TurIntegrationInstanceListPageComponent} from './component/instance/integration-instance-list-page.component';
import {FormsModule, ReactiveFormsModule} from '@angular/forms';
import {TurIntegrationRoutingModule} from './integration-routing.module';
import {TurCommonsModule} from '../commons/commons.module';
import {OcticonsModule} from 'angular-octicons';
import {RouterModule} from '@angular/router';
import {TurIntegrationRootPageComponent} from './component/root/integration-root-page.component';
import {TurIntegrationVendorService} from './service/integration-vendor.service';
import {TurLocaleService} from '../locale/service/locale.service';
import {TurIntegrationInstancePageComponent} from "./component/instance/integration-instance-page.component";
import {TurIntegrationAemPageComponent} from "./component/instance/aem/integration-aem-page.component";
import {TurIntegrationAemSourceService} from "./service/integration-aem-source.service";
import {TurIntegrationAemListPageComponent} from "./component/instance/aem/integration-aem-list-page.component";
import {ACE_CONFIG, AceConfigInterface, AceModule} from 'ngx-ace-wrapper';
import {
  TurIntegrationInstanceDetailPageComponent
} from "./component/instance/integration-instance-detail-page.component";

const DEFAULT_ACE_CONFIG: AceConfigInterface = {
  tabSize: 2,
  mode: 'ace/mode/json',
  theme: 'github',
  readOnly: false,
};

@NgModule({
  declarations: [
    TurIntegrationRootPageComponent,
    TurIntegrationInstanceDetailPageComponent,
    TurIntegrationInstanceListPageComponent,
    TurIntegrationInstancePageComponent,
    TurIntegrationAemPageComponent,
    TurIntegrationAemListPageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    OcticonsModule,
    TurIntegrationRoutingModule,
    TurCommonsModule,
    RouterModule,
    AceModule
  ],
  providers: [
    TurIntegrationInstanceService,
    TurIntegrationVendorService,
    TurLocaleService,
    TurIntegrationAemSourceService,
    {
      provide: ACE_CONFIG,
      useValue: DEFAULT_ACE_CONFIG
    },
  ],
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class TurIntegrationModule {
}
