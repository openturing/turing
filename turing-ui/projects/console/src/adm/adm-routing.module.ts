import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '../app/_helpers';
import { TurAdmGroupListPageComponent } from './component/group/adm-group-list-page.component';
import { TurAdmGroupPageComponent } from './component/group/adm-group-page.component';
import { TurAdmRootPageComponent } from './component/root/adm-root-page.component';
import { TurAdmUserListPageComponent } from './component/user/adm-user-list-page.component';
import { TurAdmUserPageComponent } from './component/user/adm-user-page.component';


const routes: Routes = [
  {
    path: '', component: TurAdmRootPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'user', component: TurAdmUserListPageComponent, canActivate: [AuthGuard] },
      { path: 'user/:username', component: TurAdmUserPageComponent, canActivate: [AuthGuard] },
      { path: 'group', component: TurAdmGroupListPageComponent, canActivate: [AuthGuard] },
      { path: 'group/:id', component: TurAdmGroupPageComponent, canActivate: [AuthGuard] },
      { path: '', redirectTo: '/adm/user', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurAdmRoutingModule { }
