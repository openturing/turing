import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TurLoginPageComponent } from './login-page';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { TurCommonsModule } from '../commons/commons.module';
import {FontAwesomeModule} from "@fortawesome/angular-fontawesome";



@NgModule({
  declarations: [TurLoginPageComponent],
    imports: [
        CommonModule,
        ReactiveFormsModule,
        FormsModule,
        TurCommonsModule,
        FontAwesomeModule
    ]
})
export class TurLoginModule { }
