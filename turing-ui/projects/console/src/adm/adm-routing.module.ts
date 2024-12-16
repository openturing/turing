import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '../app/_helpers';

import { TurAdmRootPageComponent } from './component/root/adm-root-page.component';

import { TurAdmUserPageComponent } from './component/user/adm-user-page.component';
import { TurAdmUserDetailPageComponent } from './component/user/adm-user-detail-page.component';
import { TurAdmUserGroupsPageComponent } from './component/user/adm-user-groups-page.component';
import { TurAdmUserListPageComponent } from './component/user/adm-user-list-page.component';


import { TurAdmGroupPageComponent } from './component/group/adm-group-page.component';
import { TurAdmGroupDetailPageComponent } from './component/group/adm-group-detail-page.component';
import { TurAdmGroupUsersPageComponent } from './component/group/adm-group-users-page.component';
import { TurAdmGroupListPageComponent } from './component/group/adm-group-list-page.component';

const routes: Routes = [
  {
    path: '', component: TurAdmRootPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'user', component: TurAdmUserListPageComponent, canActivate: [AuthGuard] },
      {
        path: 'user/:username', component: TurAdmUserPageComponent, canActivate: [AuthGuard],
        children: [
          { path: 'detail', component: TurAdmUserDetailPageComponent, canActivate: [AuthGuard] },
          { path: 'groups', component: TurAdmUserGroupsPageComponent, canActivate: [AuthGuard] },
          { path: '', redirectTo: 'detail' }
        ]
      },
      { path: 'group', component: TurAdmGroupListPageComponent, canActivate: [AuthGuard] },
      {
        path: 'group/:id', component: TurAdmGroupPageComponent, canActivate: [AuthGuard], children: [
          { path: 'detail', component: TurAdmGroupDetailPageComponent, canActivate: [AuthGuard] },
          { path: 'users', component: TurAdmGroupUsersPageComponent, canActivate: [AuthGuard] },
          { path: '', redirectTo: 'detail' }
        ]
      },
      { path: '', redirectTo: '/adm/user', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurAdmRoutingModule { }
