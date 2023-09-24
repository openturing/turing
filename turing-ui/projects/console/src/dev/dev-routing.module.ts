import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '../app/_helpers';
import {TurDevRootPageComponent} from './component/root/dev-root-page.component';
import {TurDevTokenListPageComponent} from "./component/token/dev-token-list-page.component";
import {TurDevTokenPageComponent} from "./component/token/dev-token-page.component";

const routes: Routes = [
  {
    path: '', component: TurDevRootPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'token', component: TurDevTokenListPageComponent, canActivate: [AuthGuard] },
      {path: 'token/:id', component: TurDevTokenPageComponent, canActivate: [AuthGuard]},
        { path: '', redirectTo: '/dev/token', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurSERoutingModule { }
