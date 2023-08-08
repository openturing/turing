import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurHeaderComponent } from './component/header/header.component';
import { TurLogoComponent } from './component/logo/logo.component';
import { IdenticonHashDirective } from '../app/directive/identicon-hash.directive';
import { RouterModule } from '@angular/router';
import { OcticonsModule } from 'angular-octicons';

@NgModule({
  declarations: [
    TurHeaderComponent,
    TurLogoComponent,
    IdenticonHashDirective
  ],
  imports: [
    CommonModule,
    RouterModule,
    OcticonsModule
  ],
  exports : [
    TurHeaderComponent,
    TurLogoComponent,
    IdenticonHashDirective
  ]

})
export class TurCommonsModule { }
