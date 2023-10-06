import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurSignupPageComponent } from './signup-page';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { TurCommonsModule } from '../commons/commons.module';



@NgModule({
  declarations: [TurSignupPageComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    FormsModule,
    TurCommonsModule
  ]
})
export class TurSignupModule { }
