import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '../app/_helpers';
import { TurAdmGroupListPageComponent } from './component/group/adm-group-list-page.component';
import { TurAdmRootPageComponent } from './component/root/adm-root-page.component';
import { TurAdmUserListPageComponent } from './component/user/adm-user-list-page.component';


const routes: Routes = [
  {
    path: '', component: TurAdmRootPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'user', component: TurAdmUserListPageComponent, canActivate: [AuthGuard] },
      { path: 'group', component: TurAdmGroupListPageComponent, canActivate: [AuthGuard] },
      { path: '', redirectTo: '/adm/user', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurAdmRoutingModule { }
