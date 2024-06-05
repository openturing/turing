import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '../app/_helpers';
import { TurIntegrationInstanceListPageComponent } from './component/instance/integration-instance-list-page.component';
import { TurIntegrationRootPageComponent } from './component/root/integration-root-page.component';
import {TurIntegrationInstancePageComponent} from "./component/instance/instance-instance-page.component";

const routes: Routes = [
  {
    path: '', component: TurIntegrationRootPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'instance', component: TurIntegrationInstanceListPageComponent, canActivate: [AuthGuard] },
      { path: 'instance/:id', component: TurIntegrationInstancePageComponent, canActivate: [AuthGuard] },
      { path: '', redirectTo: '/integration/instance', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurIntegrationRoutingModule { }
