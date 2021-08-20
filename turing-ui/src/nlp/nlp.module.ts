import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurNLPInstanceService } from './service/nlp-instance.service';
import { TurNLPInstanceListPageComponent } from './component/instance/nlp-instance-list-page.component';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { AceEditorModule } from 'ace-editor-ng9';
import { TurNLPRoutingModule } from './nlp-routing.module';
import { ShioCommonsModule } from 'src/commons/shio-commons.module';
import { OcticonsModule } from 'ngx-octicons';
import { RouterModule } from '@angular/router';
import { TurNLPEntityListPageComponent } from './component/entity/nlp-entity-list-page.component';
import { TurNLPRootPageComponent } from './component/root/nlp-root-page.component';
import { TurNLPEntityService } from './service/nlp-entity.service';
import { TurNLPVendorService } from './service/nlp-vendor.service';
import { TurNLPInstancePageComponent } from './component/instance/nlp-instance-page.component';

@NgModule({
  declarations: [
    TurNLPRootPageComponent,
    TurNLPInstancePageComponent,
    TurNLPEntityListPageComponent,
    TurNLPInstanceListPageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    AceEditorModule,
    OcticonsModule,
    TurNLPRoutingModule,
    ShioCommonsModule,
    RouterModule
  ],
  providers: [
    TurNLPInstanceService,
    TurNLPEntityService,
    TurNLPVendorService
  ]
})
export class TurNLPModule { }
