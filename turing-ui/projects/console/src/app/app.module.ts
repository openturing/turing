import { BrowserModule } from '@angular/platform-browser';
import { NgModule } from '@angular/core';

import { AppRoutingModule } from './app-routing.module';
import { HttpClientModule, HttpClientXsrfModule, HTTP_INTERCEPTORS } from '@angular/common/http';
import { AppComponent } from './page/app/app.component';
import { BasicAuthInterceptor, ErrorInterceptor } from './_helpers';
import { ReactiveFormsModule, FormsModule } from '@angular/forms';
import { NotifierModule, NotifierOptions } from "angular-notifier-updated";

import { OcticonsModule } from 'angular-octicons';
import { TurConsolePageComponent } from '../console/console-page.component';
import { TurCommonsModule } from '../commons/commons.module';
import { DragDropModule } from '@angular/cdk/drag-drop';
import { RouterModule } from '@angular/router';

const notifierDefaultOptions: NotifierOptions = {
  position: {
      horizontal: {
          position: "right",
          distance: 12
      },
      vertical: {
          position: "bottom",
          distance: 12,
          gap: 10
      }
  },
  theme: "material",
  behaviour: {
      autoHide: 5000,
      onClick: false,
      onMouseover: "pauseAutoHide",
      showDismissButton: true,
      stacking: 4
  },
  animations: {
      enabled: true,
      show: {
          preset: "slide",
          speed: 300,
          easing: "ease"
      },
      hide: {
          preset: "fade",
          speed: 300,
          easing: "ease",
          offset: 50
      },
      shift: {
          speed: 300,
          easing: "ease"
      },
      overlap: 150
  }
};
@NgModule({
  declarations: [
    AppComponent,
    TurConsolePageComponent
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    HttpClientModule,
    HttpClientXsrfModule.withOptions({
      cookieName: 'XSRF-TOKEN',
      headerName: 'X-CSRF-TOKEN'
    }),
    ReactiveFormsModule,
    FormsModule,
    NotifierModule.withConfig(notifierDefaultOptions),
    OcticonsModule,
    TurCommonsModule,
    DragDropModule,
    RouterModule
  ],
  providers: [
    { provide: HTTP_INTERCEPTORS, useClass: BasicAuthInterceptor, multi: true },
    { provide: HTTP_INTERCEPTORS, useClass: ErrorInterceptor, multi: true }
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
