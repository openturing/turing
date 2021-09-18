import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '../app/_helpers';
import { TurConverseRootPageComponent } from './component/root/converse-root-page.component';
import { TurConverseAgentListPageComponent } from './component/agent/converse-agent-list-page.component';
import { TurConverseAgentPageComponent } from './component/agent/converse-agent-page.component';
import { TurConverseAgentDetailPageComponent } from './component/agent/converse-agent-detail-page.component';

const routes: Routes = [
  {
    path: '', component: TurConverseRootPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'agent', component: TurConverseAgentListPageComponent, canActivate: [AuthGuard] },
      {
        path: 'agent/:id', component: TurConverseAgentPageComponent, canActivate: [AuthGuard],
        children: [
          { path: 'detail', component: TurConverseAgentDetailPageComponent, canActivate: [AuthGuard] },
          { path: '', redirectTo: 'detail', pathMatch: 'full' }
        ]
      },
      { path: '', redirectTo: '/converse/agent', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurConverseRoutingModule { }
