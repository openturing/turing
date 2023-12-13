import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSNSite } from '../../model/sn-site.model';
import { NotifierService } from 'angular-notifier-updated';
import { TurSNSiteService } from '../../service/sn-site.service';
import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'sn-site-list-page',
  templateUrl: './sn-site-list-page.component.html'
})
export class TurSNSiteListPageComponent implements OnInit {
  private turSNSites: Observable<TurSNSite[]>;
  filterText: string;

  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private router: Router) {
    this.turSNSites = turSNSiteService.query();
    this.filterText = "";
  }

  getTurSNSites(): Observable<TurSNSite[]> {

    return this.turSNSites;
  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
