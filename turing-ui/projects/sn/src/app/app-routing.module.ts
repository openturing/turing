import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { TurSNSearchRootPageComponent } from '../search/component/root/search-root-page.component';

const routes: Routes = [ { path: ':siteName', component: TurSNSearchRootPageComponent },];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
