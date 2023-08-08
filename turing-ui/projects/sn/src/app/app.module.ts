import { HttpClientModule } from '@angular/common/http';
import { CUSTOM_ELEMENTS_SCHEMA, NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { MomentModule } from 'ngx-moment';
import { OcticonsModule } from 'angular-octicons';
import { TurSNSearchRootPageComponent } from '../search/component/root/search-root-page.component';
import { AppRoutingModule } from './app-routing.module';
import { IdenticonHashDirective } from './directive/identicon-hash.directive';
import { TurSNSearchService } from '../search/service/sn-search.service';
import { AppComponent } from './app.component';
import { FormsModule } from '@angular/forms';
import { APP_BASE_HREF, Location } from '@angular/common';
import { SafeHtmlPipe } from './safe-html.pipe';

@NgModule({
  declarations: [
    AppComponent,
    IdenticonHashDirective,
    TurSNSearchRootPageComponent,
    SafeHtmlPipe
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    OcticonsModule,
    HttpClientModule,
    MomentModule,
    FormsModule
  ],
  providers: [
    TurSNSearchService,
    { provide: APP_BASE_HREF, useValue: (window as any)['_app_base'] || '/' }
  ],
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  bootstrap: [AppComponent]
})
export class AppModule { }
