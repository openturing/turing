import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurAdmRoutingModule } from './adm-routing.module';
import { TurCommonsModule } from '../commons/commons.module';
import { OcticonsModule } from 'ngx-octicons';
import { RouterModule } from '@angular/router';
import { TurAdmRootPageComponent } from './component/root/adm-root-page.component';
import { TurAdmUserListPageComponent } from './component/user/adm-user-list-page.component';
import { TurAdmUserService } from './service/adm-user.service';
import { TurAdmGroupService } from './service/adm-group.service';
import { TurAdmGroupListPageComponent } from './component/group/adm-group-list-page.component';
import { TurAdmUserPageComponent } from './component/user/adm-user-page.component';
import { FormsModule } from '@angular/forms';
import { TurAdmGroupPageComponent } from './component/group/adm-group-page.component';

@NgModule({
  declarations: [
    TurAdmRootPageComponent,
    TurAdmUserPageComponent,
    TurAdmUserListPageComponent,
    TurAdmGroupPageComponent,
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
