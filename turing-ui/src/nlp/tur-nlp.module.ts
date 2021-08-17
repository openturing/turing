import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurNLPInstanceService } from './service/instance/nlp-instance.service';
import { TurNLPInstancePageComponent } from './component/instance/tur-nlp-instance-page.component';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { AceEditorModule } from 'ace-editor-ng9';
import { TurNLPRoutingModule } from './tur-nlp-routing.module';
import { ShioCommonsModule } from 'src/commons/shio-commons.module';
import { OcticonsModule } from 'ngx-octicons';

@NgModule({
  declarations: [
    TurNLPInstancePageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    AceEditorModule,
    OcticonsModule,
    TurNLPRoutingModule,
    ShioCommonsModule
  ],
  exports: [
    TurNLPInstancePageComponent
  ],
  providers: [
    TurNLPInstanceService
  ]
})
export class TurNLPModule { }
