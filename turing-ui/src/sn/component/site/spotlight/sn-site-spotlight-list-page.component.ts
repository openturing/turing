import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NotifierService } from 'angular-notifier';
import { Observable } from 'rxjs';
import { TurSNSiteSpotlight } from 'src/sn/model/sn-site-spotlight.model';
import { TurSNSiteSpotlightService } from 'src/sn/service/sn-site-spotlight.service';

@Component({
  selector: 'sn-site-spotlight-list-page',
  templateUrl: './sn-site-spotlight-list-page.component.html'
})
export class TurSNSiteSpotlightListPageComponent {
  private turSNSiteSpotlights: Observable<TurSNSiteSpotlight[]>;
  private siteId: string;

  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteSpotlightService: TurSNSiteSpotlightService,
    private route: ActivatedRoute) {
    this.siteId = this.route.parent.parent.snapshot.paramMap.get('id');
    this.turSNSiteSpotlights = turSNSiteSpotlightService.query(this.siteId);
  }

  getId(): string {
    return this.siteId;
  }

  getTurSNSiteSpotlights(): Observable<TurSNSiteSpotlight[]> {
    return this.turSNSiteSpotlights;
  }
}
