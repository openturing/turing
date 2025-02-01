import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurLLMInstanceService } from './service/llm-instance.service';
import { TurLLMInstanceListPageComponent } from './component/instance/llm-instance-list-page.component';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { TurLLMRoutingModule } from './llm-routing.module';
import { TurCommonsModule } from '../commons/commons.module';
import { OcticonsModule } from 'angular-octicons';
import { RouterModule } from '@angular/router';
import { TurLLMRootPageComponent } from './component/root/llm-root-page.component';
import { TurLLMVendorService } from './service/llm-vendor.service';
import { TurLLMInstancePageComponent } from './component/instance/llm-instance-page.component';
import { TurLocaleService } from '../locale/service/locale.service';
@NgModule({
  declarations: [
    TurLLMRootPageComponent,
    TurLLMInstancePageComponent,
    TurLLMInstanceListPageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    OcticonsModule,
    TurLLMRoutingModule,
    TurCommonsModule,
    RouterModule
  ],
  providers: [
    TurLLMInstanceService,
    TurLLMVendorService,
    TurLocaleService
  ],
  schemas: [ CUSTOM_ELEMENTS_SCHEMA ]
})
export class TurLLMModule { }
