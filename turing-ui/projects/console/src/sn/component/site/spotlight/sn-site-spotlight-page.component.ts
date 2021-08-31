import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSNSite } from '../../../model/sn-site.model';
import { NotifierService } from 'angular-notifier';
import { TurSNSiteService } from '../../../service/sn-site.service';
import { ActivatedRoute } from '@angular/router';
import { TurSNSiteSpotlight } from '../../../model/sn-site-spotlight.model';
import { TurSNSiteSpotlightService } from '../../../service/sn-site-spotlight.service';

@Component({
  selector: 'sn-site-spotlight-page',
  templateUrl: './sn-site-spotlight-page.component.html'
})
export class TurSNSiteSpotlightPageComponent implements OnInit {
  private turSNSite: Observable<TurSNSite>;
  private turSNSiteSpotlight: Observable<TurSNSiteSpotlight>;
  private siteId: string;

  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private turSNSiteSpotlightService: TurSNSiteSpotlightService,
    private route: ActivatedRoute) {
    this.siteId = this.route.parent?.parent?.snapshot.paramMap.get('id') || "";
    let spotlightId = this.route.snapshot.paramMap.get('spotlightId') || "";
    this.turSNSite = this.turSNSiteService.get(this.siteId);
    this.turSNSiteSpotlight = this.turSNSiteSpotlightService.get(this.siteId, spotlightId);
  }

  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }

  getTurSNSiteSpotlight(): Observable<TurSNSiteSpotlight> {
    return this.turSNSiteSpotlight;
  }

  ngOnInit(): void {
  }

  public saveSiteField(_turSNSiteSpotlight: TurSNSiteSpotlight) {
    this.turSNSiteSpotlightService.save(this.siteId, _turSNSiteSpotlight).subscribe(
      (turSNSiteSpotlight: TurSNSiteSpotlight) => {
        _turSNSiteSpotlight = turSNSiteSpotlight;
        this.notifier.notify("success", turSNSiteSpotlight.name.concat(" semantic navigation site spotlight was updated."));
      },
      response => {
        this.notifier.notify("error", "Semantic navigation site spotlight was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }
}
