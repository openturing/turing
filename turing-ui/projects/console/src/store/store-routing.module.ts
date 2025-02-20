import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '../app/_helpers';
import { TurStoreInstancePageComponent } from './component/instance/store-instance-page.component';
import { TurStoreInstanceListPageComponent } from './component/instance/store-instance-list-page.component';
import { TurStoreRootPageComponent } from './component/root/store-root-page.component';

const routes: Routes = [
  {
    path: '', component: TurStoreRootPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'instance', component: TurStoreInstanceListPageComponent, canActivate: [AuthGuard] },
      { path: 'instance/:id', component: TurStoreInstancePageComponent, canActivate: [AuthGuard] },
      { path: '', redirectTo: '/store/instance', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurStoreRoutingModule { }
