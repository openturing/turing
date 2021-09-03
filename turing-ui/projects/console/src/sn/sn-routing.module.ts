import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '../app/_helpers';
import { TurSNSitePageComponent } from './component/site/sn-site-page.component';
import { TurSNSiteListPageComponent } from './component/site/sn-site-list-page.component';
import { TurSNRootPageComponent } from './component/root/sn-root-page.component';
import { TurSNSiteDetailPageComponent } from './component/site/sn-site-detail-page.component';
import { TurSNSiteUIPageComponent } from './component/site/sn-site-ui-page.component';
import { TurSNSiteFieldRootPageComponent } from './component/site/field/sn-site-field-root-page.component';
import { TurSNSiteFieldListPageComponent } from './component/site/field/sn-site-field-list-page.component';
import { TurSNSiteFieldPageComponent } from './component/site/field/sn-site-field-page.component';
import { TurSNSiteSpotlightRootPageComponent } from './component/site/spotlight/sn-site-spotlight-root-page.component';
import { TurSNSiteSpotlightListPageComponent } from './component/site/spotlight/sn-site-spotlight-list-page.component';
import { TurSNSiteSpotlightPageComponent } from './component/site/spotlight/sn-site-spotlight-page.component';

const routes: Routes = [
  {
    path: '', component: TurSNRootPageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'site', component: TurSNSiteListPageComponent, canActivate: [AuthGuard] },
      {
        path: 'site/:id', component: TurSNSitePageComponent, canActivate: [AuthGuard],
        children: [
          {
            path: 'spotlight', component: TurSNSiteSpotlightRootPageComponent, canActivate: [AuthGuard],
            children: [
              { path: 'list', component: TurSNSiteSpotlightListPageComponent, canActivate: [AuthGuard] },
              { path: 'edit/:spotlightId', component: TurSNSiteSpotlightPageComponent, canActivate: [AuthGuard] },
              { path: '', redirectTo: 'list', pathMatch: 'full' }
            ]
          },
          { path: 'detail', component: TurSNSiteDetailPageComponent, canActivate: [AuthGuard] },
          {
            path: 'field', component: TurSNSiteFieldRootPageComponent, canActivate: [AuthGuard],
            children: [
              { path: 'list', component: TurSNSiteFieldListPageComponent, canActivate: [AuthGuard] },
              { path: 'edit/:fieldId', component: TurSNSiteFieldPageComponent, canActivate: [AuthGuard] },
              { path: '', redirectTo: 'list', pathMatch: 'full' }
            ]
          },
          { path: 'ui', component: TurSNSiteUIPageComponent, canActivate: [AuthGuard] },
          { path: '', redirectTo: 'detail', pathMatch: 'full' }
        ]
      },
      { path: '', redirectTo: '/sn/site', pathMatch: 'full' }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurSNRoutingModule { }
