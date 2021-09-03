import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurAdmRoutingModule } from './adm-routing.module';
import { ShioCommonsModule } from '../commons/commons.module';
import { OcticonsModule } from 'ngx-octicons';
import { RouterModule } from '@angular/router';

@NgModule({
  declarations: [
  ],
  imports: [
    CommonModule,
    OcticonsModule,
    TurAdmRoutingModule,
    ShioCommonsModule,
    RouterModule,
  ],
  providers: [
  ],
  schemas: [ CUSTOM_ELEMENTS_SCHEMA ]
})
export class TurAdmModule { }
