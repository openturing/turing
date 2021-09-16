import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurLoginPageComponent } from './login-page';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { TurCommonsModule } from '../commons/commons.module';



@NgModule({
  declarations: [TurLoginPageComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    TurCommonsModule
  ]
})
export class TurLoginModule { }
