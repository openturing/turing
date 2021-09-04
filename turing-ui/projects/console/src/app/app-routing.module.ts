import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { AuthGuard } from './_helpers';
import { TurLoginPageComponent } from '../login/login-page';
import { TurConsolePageComponent } from './../console/console-page.component';

const routes: Routes = [
  { path: 'login', component: TurLoginPageComponent },
  { path: 'adm', loadChildren: () => import('../adm/adm.module').then(m => m.TurAdmModule) },
  {
    path: '', component: TurConsolePageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'nlp', loadChildren: () => import('../nlp/nlp.module').then(m => m.TurNLPModule) },
      { path: 'se', loadChildren: () => import('../se/se.module').then(m => m.TurSEModule) },
      { path: 'sn', loadChildren: () => import('../sn/sn.module').then(m => m.TurSNModule) },
      { path: '', redirectTo: '/nlp', pathMatch: 'full' }
    ]
  },
  { path: '', redirectTo: '/nlp', pathMatch: 'full' }
];
@NgModule({
  imports: [RouterModule.forRoot(routes, { useHash: false })],
  exports: [RouterModule]
})
export class AppRoutingModule { }
