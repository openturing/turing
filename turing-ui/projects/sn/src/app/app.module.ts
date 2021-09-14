import { HttpClientModule } from '@angular/common/http';
import { NgModule } from '@angular/core';
import { BrowserModule } from '@angular/platform-browser';
import { MomentModule } from 'ngx-moment';
import { OcticonsModule } from 'ngx-octicons';
import { TurSNSearchRootPageComponent } from '../search/component/root/search-root-page.component';
import { AppRoutingModule } from './app-routing.module';
import { IdenticonHashDirective } from './directive/identicon-hash.directive';
import { TurSNSearchService } from '../search/service/sn-search.service';
import { AppComponent } from './app.component';
import { FormsModule } from '@angular/forms';

@NgModule({
  declarations: [
    AppComponent,
    IdenticonHashDirective,
    TurSNSearchRootPageComponent
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
    TurSNSearchService
  ],
  bootstrap: [AppComponent]
})
export class AppModule { }
