import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { TurLoginPageComponent } from './login/login-page';
import { TurSignupPageComponent } from './signup/signup-page';
import { TurPasswordResetPageComponent } from './password-reset/password-reset-page';

const routes: Routes = [
  { path: '', component: TurLoginPageComponent },
  { path: 'signup', component: TurSignupPageComponent },
  { path: 'password_reset', component: TurPasswordResetPageComponent }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
