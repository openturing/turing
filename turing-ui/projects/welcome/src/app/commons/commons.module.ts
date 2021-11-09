import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurLogoComponent } from './component/logo/logo.component';
import { RouterModule } from '@angular/router';
import { OcticonsModule } from 'ngx-octicons';

@NgModule({
  declarations: [
    TurLogoComponent
  ],
  imports: [
    CommonModule,
    RouterModule,
    OcticonsModule
  ],
  exports : [
    TurLogoComponent
  ]

})
export class TurCommonsModule { }
