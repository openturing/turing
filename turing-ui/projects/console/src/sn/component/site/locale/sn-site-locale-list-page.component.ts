import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NotifierService } from 'angular-notifier';
import { Observable } from 'rxjs';
import { TurSNSiteLocale } from '../../../model/sn-site-locale.model';
import { TurSNSiteLocaleService } from '../../../service/sn-site-locale.service';

@Component({
  selector: 'sn-site-locale-list-page',
  templateUrl: './sn-site-locale-list-page.component.html'
})
export class TurSNSiteLocaleListPageComponent {
  private turSNSiteLocales: Observable<TurSNSiteLocale[]>;
  private siteId: string;

  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteLocaleService: TurSNSiteLocaleService,
    private route: ActivatedRoute) {
    this.siteId = this.route.parent?.parent?.snapshot.paramMap.get('id') || "";
    this.turSNSiteLocales = turSNSiteLocaleService.query(this.siteId);
  }

  getId(): string {
    return this.siteId;
  }

  getTurSNSiteLocales(): Observable<TurSNSiteLocale[]> {
    return this.turSNSiteLocales;
  }
}
