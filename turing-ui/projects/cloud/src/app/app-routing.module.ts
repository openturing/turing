import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { AuthGuard } from './_helpers';
import { TurConsolePageComponent } from './../console/console-page.component';

const routes: Routes = [
  {
    path: '', component: TurConsolePageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'sn', loadChildren: () => import('../../../console/src/sn/sn.module').then(m => m.TurSNModule) },
      { path: '', redirectTo: '/sn', pathMatch: 'full' }
    ]
  },
  { path: '', redirectTo: '/nlp', pathMatch: 'full' }
];
@NgModule({
  imports: [RouterModule.forRoot(routes, { useHash: false})],
  exports: [RouterModule]
})
export class AppRoutingModule { }
