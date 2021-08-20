import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '@app/_helpers';
import { TurSNSitePageComponent } from './component/site/sn-site-page.component';
import { TurSNSiteListPageComponent } from './component/site/sn-site-list-page.component';
import { TurSNRootPageComponent } from './component/root/sn-root-page.component';

const routes: Routes = [
  {
    path: '', component: TurSNRootPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'site', component: TurSNSiteListPageComponent, canActivate: [AuthGuard] },
      { path: 'site/:id', component: TurSNSitePageComponent, canActivate: [AuthGuard] },
      { path: '', redirectTo: '/console/sn/site', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurSNRoutingModule { }
