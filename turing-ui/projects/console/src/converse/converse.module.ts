import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MomentModule } from 'ngx-moment';
import { TurConverseAgentService } from './service/converse-agent.service';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { TurCommonsModule } from '../commons/commons.module';
import { OcticonsModule } from 'ngx-octicons';
import { RouterModule } from '@angular/router';
import { TurLocaleService } from '../locale/service/locale.service';
import { TurSEInstanceService } from '../se/service/se-instance.service';
import { TurConverseRoutingModule } from './converse-routing.module';
import { TurConverseRootPageComponent } from './component/root/converse-root-page.component';
import { TurConverseAgentListPageComponent } from './component/agent/converse-agent-list-page.component';
import { TurConverseAgentPageComponent } from './component/agent/converse-agent-page.component';
import { TurConverseAgentDetailPageComponent } from './component/agent/converse-agent-detail-page.component';

@NgModule({
  declarations: [
    TurConverseRootPageComponent,
    TurConverseAgentListPageComponent,
    TurConverseAgentPageComponent,
    TurConverseAgentDetailPageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    OcticonsModule,
    TurConverseRoutingModule,
    TurCommonsModule,
    RouterModule,
    MomentModule
  ],
  providers: [
    TurConverseAgentService,
    TurSEInstanceService,
    TurLocaleService
  ],
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class TurConverseModule { }
