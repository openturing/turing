import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '../app/_helpers';
import { TurNLPInstancePageComponent } from './component/instance/nlp-instance-page.component';
import { TurNLPInstanceListPageComponent } from './component/instance/nlp-instance-list-page.component';
import { TurNLPEntityListPageComponent } from './component/entity/nlp-entity-list-page.component';
import { TurNLPRootPageComponent } from './component/root/nlp-root-page.component';
import { TurNLPEntityPageComponent } from './component/entity/nlp-entity-page.component';

const routes: Routes = [
  {
    path: '', component: TurNLPRootPageComponent, canActivate: [AuthGuard],
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurAdmRoutingModule { }
