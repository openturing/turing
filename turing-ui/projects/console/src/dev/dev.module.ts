import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { TurSERoutingModule } from './dev-routing.module';
import { TurCommonsModule } from '../commons/commons.module';
import { OcticonsModule } from 'angular-octicons';
import { RouterModule } from '@angular/router';
import {TurDevTokenListPageComponent} from "./component/token/dev-token-list-page.component";
import {TurDevTokenPageComponent} from "./component/token/dev-token-page.component";
import {TurDevTokenService} from "./service/dev-token.service";
import {TurDevRootPageComponent} from "./component/root/dev-root-page.component";
@NgModule({
  declarations: [
    TurDevRootPageComponent,
    TurDevTokenListPageComponent,
    TurDevTokenPageComponent
  ],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    OcticonsModule,
    TurSERoutingModule,
    TurCommonsModule,
    RouterModule
  ],
  providers: [
    TurDevTokenService
  ],
  schemas: [ CUSTOM_ELEMENTS_SCHEMA ]
})
export class TurDevModule { }
