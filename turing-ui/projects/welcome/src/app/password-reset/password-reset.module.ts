import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurPasswordResetPageComponent } from './password-reset-page';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { TurCommonsModule } from '../commons/commons.module';



@NgModule({
  declarations: [TurPasswordResetPageComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    TurCommonsModule
  ]
})
export class TurPasswordResetModule { }
