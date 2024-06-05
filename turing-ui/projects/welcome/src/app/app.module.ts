import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
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

@NgModule({ declarations: [
        AppComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
    bootstrap: [AppComponent], imports: [BrowserModule,
        AppRoutingModule,
        OcticonsModule,
        MomentModule,
        FormsModule,
        TurLoginModule,
        TurSignupModule,
        TurPasswordResetModule], providers: [
        TurSignupService,
        provideHttpClient(withInterceptorsFromDi())
    ] })
export class AppModule { }
