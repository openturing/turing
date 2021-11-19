import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSNSite } from '../../../model/sn-site.model';
import { NotifierService } from 'angular-notifier';
import { TurSNSiteService } from '../../../service/sn-site.service';
import { ActivatedRoute } from '@angular/router';
import { TurSNSiteLocale } from '../../../model/sn-site-locale.model';
import { TurSNSiteLocaleService } from '../../../service/sn-site-locale.service';

@Component({
  selector: 'sn-site-locale-page',
  templateUrl: './sn-site-locale-page.component.html'
})
export class TurSNSiteLocalePageComponent implements OnInit {
  private turSNSite: Observable<TurSNSite>;
  private turSNSiteLocale: Observable<TurSNSiteLocale>;
  private siteId: string;

  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private turSNSiteLocaleService: TurSNSiteLocaleService,
    private route: ActivatedRoute) {
    this.siteId = this.route.parent?.parent?.snapshot.paramMap.get('id') || "";
    let localeId = this.route.snapshot.paramMap.get('localeId') || "";
    this.turSNSite = this.turSNSiteService.get(this.siteId);
    this.turSNSiteLocale = this.turSNSiteLocaleService.get(this.siteId, localeId);
  }

  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }

  getTurSNSiteLocale(): Observable<TurSNSiteLocale> {
    return this.turSNSiteLocale;
  }

  ngOnInit(): void {
  }

  public saveSiteField(_turSNSiteLocale: TurSNSiteLocale) {
    this.turSNSiteLocaleService.save(this.siteId, _turSNSiteLocale).subscribe(
      (turSNSiteLocale: TurSNSiteLocale) => {
        _turSNSiteLocale = turSNSiteLocale;
        this.notifier.notify("success", turSNSiteLocale.language.concat(" semantic navigation site locale was updated."));
      },
      response => {
        this.notifier.notify("error", "Semantic navigation site locale was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }
}
