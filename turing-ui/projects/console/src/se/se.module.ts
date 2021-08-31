import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurSEInstanceService } from './service/se-instance.service';
import { TurSEInstanceListPageComponent } from './component/instance/se-instance-list-page.component';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { TurSERoutingModule } from './se-routing.module';
import { ShioCommonsModule } from '../commons/shio-commons.module';
import { OcticonsModule } from 'ngx-octicons';
import { RouterModule } from '@angular/router';
import { TurSERootPageComponent } from './component/root/se-root-page.component';
import { TurSEVendorService } from './service/se-vendor.service';
import { TurSEInstancePageComponent } from './component/instance/se-instance-page.component';
import { TurLocaleService } from '../locale/service/locale.service';
@NgModule({
  declarations: [
    TurSERootPageComponent,
    TurSEInstancePageComponent,
    TurSEInstanceListPageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    OcticonsModule,
    TurSERoutingModule,
    ShioCommonsModule,
    RouterModule
  ],
  providers: [
    TurSEInstanceService,
    TurSEVendorService,
    TurLocaleService
  ],
  schemas: [ CUSTOM_ELEMENTS_SCHEMA ]
})
export class TurSEModule { }
