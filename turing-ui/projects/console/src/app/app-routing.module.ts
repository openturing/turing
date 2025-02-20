import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';
import { AuthGuard } from './_helpers';
import { TurConsolePageComponent } from '../console/console-page.component';

const routes: Routes = [
  { path: 'adm', loadChildren: () => import('../adm/adm.module').then(m => m.TurAdmModule) },
  {
    path: '', component: TurConsolePageComponent, canActivate: [AuthGuard],
    children: [
      { path: 'se', loadChildren: () => import('../se/se.module').then(m => m.TurSEModule) },
      { path: 'llm', loadChildren: () => import('../llm/llm.module').then(m => m.TurLLMModule) },
      { path: 'store', loadChildren: () => import('../store/store.module').then(m => m.TurStoreModule) },
      { path: 'sn', loadChildren: () => import('../sn/sn.module').then(m => m.TurSNModule) },
      { path: 'integration', loadChildren: () => import('../integration/integration.module').then(m => m.TurIntegrationModule) },
      { path: 'dev', loadChildren: () => import('../dev/dev.module').then(m => m.TurDevModule) },
      { path: '', redirectTo: '/sn', pathMatch: 'full' }
    ]
  },
  { path: '', redirectTo: '/sn', pathMatch: 'full' }
];
@NgModule({
  imports: [RouterModule.forRoot(routes, { useHash: false})],
  exports: [RouterModule]
})
export class AppRoutingModule { }
