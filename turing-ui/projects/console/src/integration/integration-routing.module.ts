import {NgModule} from '@angular/core';
import {RouterModule, Routes} from '@angular/router';

import {AuthGuard} from '../app/_helpers';
import {TurIntegrationInstanceListPageComponent} from './component/instance/integration-instance-list-page.component';
import {TurIntegrationRootPageComponent} from './component/root/integration-root-page.component';
import {TurIntegrationAemListPageComponent} from "./component/instance/aem/integration-aem-list-page.component";
import {TurIntegrationAemPageComponent} from "./component/instance/aem/integration-aem-page.component";
import {
  TurIntegrationInstanceDetailPageComponent
} from "./component/instance/integration-instance-detail-page.component";
import {TurIntegrationAemMappingPageComponent} from "./component/instance/aem/integration-aem-mapping-page.component";
import {TurIntegrationAemMenuPageComponent} from "./component/instance/aem/integration-aem-menu-page.component";
import {TurIntegrationWcMenuPageComponent} from "./component/instance/wc/integration-wc-menu-page.component";
import {TurIntegrationWcPageComponent} from "./component/instance/wc/integration-wc-page.component";
import {TurIntegrationWcListPageComponent} from "./component/instance/wc/integration-wc-list-page.component";

const routes: Routes = [
  {
    path: '', component: TurIntegrationRootPageComponent, canActivate: [AuthGuard],
    children: [
      {path: 'instance', component: TurIntegrationInstanceListPageComponent, canActivate: [AuthGuard]},
      {
        path: 'aem/:id', component: TurIntegrationAemMenuPageComponent, canActivate: [AuthGuard],
        children: [
          {path: 'mapping', component: TurIntegrationAemMappingPageComponent, canActivate: [AuthGuard]},
          {path: 'instance', component: TurIntegrationAemListPageComponent, canActivate: [AuthGuard]},
          {path: 'instance/:aemId', component: TurIntegrationAemPageComponent, canActivate: [AuthGuard]},
          {path: 'detail', component: TurIntegrationInstanceDetailPageComponent, canActivate: [AuthGuard]},
          {path: '', redirectTo: 'detail', pathMatch: 'full'}
        ]
      },
      {
        path: 'web-crawler/:id', component: TurIntegrationWcMenuPageComponent, canActivate: [AuthGuard],
        children: [
          {path: 'mapping', component: TurIntegrationAemMappingPageComponent, canActivate: [AuthGuard]},
          {path: 'instance', component: TurIntegrationWcListPageComponent, canActivate: [AuthGuard]},
          {path: 'instance/:wcId', component: TurIntegrationWcPageComponent, canActivate: [AuthGuard]},
          {path: 'detail', component: TurIntegrationInstanceDetailPageComponent, canActivate: [AuthGuard]},
          {path: '', redirectTo: 'detail', pathMatch: 'full'}
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
