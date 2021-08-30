import { Component } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { NotifierService } from 'angular-notifier';
import { Observable } from 'rxjs';
import { TurSNSiteSpotlight } from 'src/sn/model/sn-site-spotlight.model';
import { TurSNSiteSpotlightService } from 'src/sn/service/sn-site-spotlight.service';

@Component({
  selector: 'sn-site-spotlight-page',
  templateUrl: './sn-site-spotlight-root-page.component.html'
})
export class TurSNSiteSpotlightRootPageComponent {
  private turSNSiteSpotlights: Observable<TurSNSiteSpotlight[]>;
  private siteId: string;

  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteSpotlightService: TurSNSiteSpotlightService,
    private route: ActivatedRoute) {
    this.siteId = this.route.parent.snapshot.paramMap.get('id');
    this.turSNSiteSpotlights = turSNSiteSpotlightService.query(this.siteId);
  }

  getTurSNSiteSpotlights(): Observable<TurSNSiteSpotlight[]> {
    return this.turSNSiteSpotlights;
  }
}
