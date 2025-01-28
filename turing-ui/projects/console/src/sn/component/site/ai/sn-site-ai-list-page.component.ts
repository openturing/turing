import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NotifierService } from 'angular-notifier-updated';
import { Observable } from 'rxjs';
import { TurSNSiteSpotlight } from '../../../model/sn-site-spotlight.model';
import { TurSNSiteSpotlightService } from '../../../service/sn-site-spotlight.service';

@Component({
    selector: 'sn-site-ai-list-page',
    templateUrl: './sn-site-ai-list-page.component.html',
    standalone: false
})
export class TurSNSiteAIListPageComponent {
  private turSNSiteSpotlights: Observable<TurSNSiteSpotlight[]>;
  private siteId: string;

  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteSpotlightService: TurSNSiteSpotlightService,
    private route: ActivatedRoute) {
    this.siteId = this.route.parent?.parent?.snapshot.paramMap.get('id') || "";
    this.turSNSiteSpotlights = turSNSiteSpotlightService.query(this.siteId);
  }

  getId(): string {
    return this.siteId;
  }

  getTurSNSiteSpotlights(): Observable<TurSNSiteSpotlight[]> {
    return this.turSNSiteSpotlights;
  }
}
