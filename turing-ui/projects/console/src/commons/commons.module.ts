import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TurHeaderComponent} from './component/header/header.component';
import {TurLogoComponent} from './component/logo/logo.component';
import {IdenticonHashDirective} from '../app/directive/identicon-hash.directive';
import {RouterModule} from '@angular/router';
import {OcticonsModule} from 'angular-octicons';
import {FormsModule} from "@angular/forms";
import {FullTextSearchPipe} from "./pipe/fullTextSearch.pipe";

@NgModule({
  declarations: [
    TurHeaderComponent,
    TurLogoComponent,
    IdenticonHashDirective,
    FullTextSearchPipe
  ],
  imports: [
    CommonModule,
    RouterModule,
    OcticonsModule,
    FormsModule
  ],
  exports: [

    TurHeaderComponent,
    TurLogoComponent,
    IdenticonHashDirective,
    FullTextSearchPipe
  ]

})
export class TurCommonsModule {
}
