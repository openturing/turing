import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { TurLoginPageComponent } from './login/login-page';

const routes: Routes = [{ path: '', component: TurLoginPageComponent }];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
