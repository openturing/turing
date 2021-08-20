import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '@app/_helpers';
import { TurNLPInstancePageComponent } from './component/instance/nlp-instance-page.component';
import { TurNLPInstanceListPageComponent } from './component/instance/nlp-instance-list-page.component';
import { TurNLPEntityListPageComponent } from './component/entity/nlp-entity-list-page.component';
import { TurNLPRootPageComponent } from './component/root/nlp-root-page.component';

const routes: Routes = [
  {
    path: '', component: TurNLPRootPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'instance', component: TurNLPInstanceListPageComponent, canActivate: [AuthGuard] },
      { path: 'instance/:id', component: TurNLPInstancePageComponent, canActivate: [AuthGuard] },
      { path: 'entity', component: TurNLPEntityListPageComponent, canActivate: [AuthGuard] },
      { path: '', redirectTo: '/console/nlp/instance', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurNLPRoutingModule { }
