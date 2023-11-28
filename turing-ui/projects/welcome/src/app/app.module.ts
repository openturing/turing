import { HttpClientModule } from '@angular/common/http';
import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { MomentModule } from 'ngx-moment';
import { OcticonsModule } from 'angular-octicons';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { FormsModule } from '@angular/forms';
import { TurLoginModule } from './login/login.module';
import { TurSignupModule } from './signup/signup.module';
import { TurPasswordResetModule } from './password-reset/password-reset.module';
import { TurSignupService } from './_services/signup.service';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';

@NgModule({
  declarations: [
    AppComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    OcticonsModule,
    HttpClientModule,
    MomentModule,
    FormsModule,
    TurLoginModule,
    TurSignupModule,
    TurPasswordResetModule,
    FontAwesomeModule
  ],
  providers: [
    TurSignupService
  ],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  bootstrap: [AppComponent]
})
export class AppModule { }
