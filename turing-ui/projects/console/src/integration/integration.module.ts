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
import {TurIntegrationAEMPageComponent} from "./component/instance/aem/integration-aem-page.component";

@NgModule({
  declarations: [
    TurIntegrationRootPageComponent,
    TurIntegrationInstanceListPageComponent,
    TurIntegrationInstancePageComponent,
    TurIntegrationAEMPageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    OcticonsModule,
    TurIntegrationRoutingModule,
    TurCommonsModule,
    RouterModule
  ],
  providers: [
    TurIntegrationInstanceService,
    TurIntegrationVendorService,
    TurLocaleService
  ],
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class TurIntegrationModule {
}
