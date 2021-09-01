import { HttpClientModule } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { MomentModule } from 'ngx-moment';
import { OcticonsModule } from 'ngx-octicons';
import { AppRoutingModule } from './app-routing.module';
import { AppComponent } from './app.component';
import { IdenticonHashDirective } from './directive/identicon-hash.directive';
import { TurSNSearchService } from './service/sn-search.service';

@NgModule({
  declarations: [
    AppComponent,
    IdenticonHashDirective
  ],
  imports: [
    BrowserModule,
    AppRoutingModule,
    OcticonsModule,
    HttpClientModule,
    MomentModule
  ],
  providers: [
    TurSNSearchService
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
