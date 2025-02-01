import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '../app/_helpers';
import { TurLLMInstancePageComponent } from './component/instance/llm-instance-page.component';
import { TurLLMInstanceListPageComponent } from './component/instance/llm-instance-list-page.component';
import { TurLLMRootPageComponent } from './component/root/llm-root-page.component';

const routes: Routes = [
  {
    path: '', component: TurLLMRootPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'instance', component: TurLLMInstanceListPageComponent, canActivate: [AuthGuard] },
      { path: 'instance/:id', component: TurLLMInstancePageComponent, canActivate: [AuthGuard] },
      { path: '', redirectTo: '/llm/instance', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurLLMRoutingModule { }
