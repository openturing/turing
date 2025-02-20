import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurStoreInstanceService } from './service/store-instance.service';
import { TurStoreInstanceListPageComponent } from './component/instance/store-instance-list-page.component';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { TurStoreRoutingModule } from './store-routing.module';
import { TurCommonsModule } from '../commons/commons.module';
import { OcticonsModule } from 'angular-octicons';
import { RouterModule } from '@angular/router';
import { TurStoreRootPageComponent } from './component/root/store-root-page.component';
import { TurStoreVendorService } from './service/store-vendor.service';
import { TurStoreInstancePageComponent } from './component/instance/store-instance-page.component';
import { TurLocaleService } from '../locale/service/locale.service';
@NgModule({
  declarations: [
    TurStoreRootPageComponent,
    TurStoreInstancePageComponent,
    TurStoreInstanceListPageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    OcticonsModule,
    TurStoreRoutingModule,
    TurCommonsModule,
    RouterModule
  ],
  providers: [
    TurStoreInstanceService,
    TurStoreVendorService,
    TurLocaleService
  ],
  schemas: [ CUSTOM_ELEMENTS_SCHEMA ]
})
export class TurStoreModule { }
