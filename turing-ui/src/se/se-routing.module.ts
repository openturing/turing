import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '@app/_helpers';
import { TurSEInstancePageComponent } from './component/instance/se-instance-page.component';
import { TurSEInstanceListPageComponent } from './component/instance/se-instance-list-page.component';
import { TurSERootPageComponent } from './component/root/se-root-page.component';

const routes: Routes = [
  {
    path: '', component: TurSERootPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'instance', component: TurSEInstanceListPageComponent, canActivate: [AuthGuard] },
      { path: 'instance/:id', component: TurSEInstancePageComponent, canActivate: [AuthGuard] },
      { path: '', redirectTo: '/console/se/instance', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurSERoutingModule { }
