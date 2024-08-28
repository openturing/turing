import { provideHttpClient, withInterceptorsFromDi } from '@angular/common/http';
import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { MomentModule } from 'ngx-moment';
import { OcticonsModule } from 'angular-octicons';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { FormsModule } from '@angular/forms';
import { TurLoginModule } from './login/login.module';

@NgModule({ declarations: [
        AppComponent
    ],
    schemas: [CUSTOM_ELEMENTS_SCHEMA],
    bootstrap: [AppComponent], imports: [BrowserModule,
        AppRoutingModule,
        OcticonsModule,
        MomentModule,
        FormsModule,
        TurLoginModule], providers: [
        provideHttpClient(withInterceptorsFromDi())
    ] })
export class AppModule { }
