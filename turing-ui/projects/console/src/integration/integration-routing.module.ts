import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';

import {AuthGuard} from '../app/_helpers';
import {TurIntegrationInstanceListPageComponent} from './component/instance/integration-instance-list-page.component';
import {TurIntegrationRootPageComponent} from './component/root/integration-root-page.component';
import {TurIntegrationInstancePageComponent} from "./component/instance/integration-instance-page.component";
import {TurIntegrationAemListPageComponent} from "./component/instance/aem/integration-aem-list-page.component";
import {TurIntegrationAemPageComponent} from "./component/instance/aem/integration-aem-page.component";
import {
  TurIntegrationInstanceDetailPageComponent
} from "./component/instance/integration-instance-detail-page.component";

const routes: Routes = [
  {
    path: '', component: TurIntegrationRootPageComponent, canActivate: [AuthGuard],
    children: [
      {path: 'instance', component: TurIntegrationInstanceListPageComponent, canActivate: [AuthGuard]},
      {path: 'instance/:id', component: TurIntegrationInstancePageComponent, canActivate: [AuthGuard],
        children: [
          {path: '', redirectTo: 'detail', pathMatch: 'full'},
          {
            path: 'detail', component: TurIntegrationInstanceDetailPageComponent, canActivate: [AuthGuard]
          },
          {
            path: 'aem', component: TurIntegrationRootPageComponent, canActivate: [AuthGuard],
            children: [
              {path: 'list', component: TurIntegrationAemListPageComponent, canActivate: [AuthGuard]},
              {path: ':aemId', component: TurIntegrationAemPageComponent, canActivate: [AuthGuard]},
              {path: '', redirectTo: 'list', pathMatch: 'full'}
            ]
          }
        ]
      },
      {path: '', redirectTo: '/integration/instance', pathMatch: 'full'}
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurIntegrationRoutingModule {
}
