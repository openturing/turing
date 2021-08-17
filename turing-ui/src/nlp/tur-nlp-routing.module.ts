import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { AuthGuard } from '@app/_helpers';
import { TurNLPInstancePageComponent } from './component/instance/tur-nlp-instance-page.component';


const routes: Routes = [
  {
    path: 'instance/:id',
    component: TurNLPInstancePageComponent,
    canActivate: [AuthGuard]
  },
  { path: '', redirectTo: '/console/nlp/instance' },
  { path: 'instance', component: TurNLPInstancePageComponent, canActivate: [AuthGuard] },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class TurNLPRoutingModule { }
