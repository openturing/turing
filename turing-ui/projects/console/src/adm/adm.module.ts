import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurAdmRoutingModule } from './adm-routing.module';
import { TurCommonsModule } from '../commons/commons.module';
import { OcticonsModule } from 'angular-octicons';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';

import { TurAdmUserService } from './service/adm-user.service';
import { TurAdmGroupService } from './service/adm-group.service';

import { TurAdmRootPageComponent } from './component/root/adm-root-page.component';

import { TurAdmUserPageComponent } from './component/user/adm-user-page.component';
import { TurAdmUserDetailPageComponent } from './component/user/adm-user-detail-page.component';
import { TurAdmUserGroupsPageComponent } from './component/user/adm-user-groups-page.component';
import { TurAdmUserListPageComponent } from './component/user/adm-user-list-page.component';

import { TurAdmGroupPageComponent } from './component/group/adm-group-page.component';
import { TurAdmGroupDetailPageComponent } from './component/group/adm-group-detail-page.component';
import { TurAdmGroupUsersPageComponent } from './component/group/adm-group-users-page.component';
import { TurAdmGroupListPageComponent } from './component/group/adm-group-list-page.component';

@NgModule({
  declarations: [
    TurAdmRootPageComponent,
    TurAdmUserPageComponent,
    TurAdmUserDetailPageComponent,
    TurAdmUserGroupsPageComponent,
    TurAdmUserListPageComponent,
    TurAdmGroupPageComponent,
    TurAdmGroupDetailPageComponent,
    TurAdmGroupUsersPageComponent,
    TurAdmGroupListPageComponent
  ],
  imports: [
    CommonModule,
    OcticonsModule,
    TurAdmRoutingModule,
    TurCommonsModule,
    RouterModule,
    FormsModule
  ],
  providers: [
    TurAdmUserService,
    TurAdmGroupService
  ],
  schemas: [CUSTOM_ELEMENTS_SCHEMA]
})
export class TurAdmModule { }
