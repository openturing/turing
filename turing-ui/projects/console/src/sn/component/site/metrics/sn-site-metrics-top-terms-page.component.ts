import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NotifierService } from 'angular-notifier';
import { Observable } from 'rxjs';
import { TurSNSiteMetricsTerm } from '../../../model/sn-site-metrics-term.model';
import { TurSNSiteMetricsService } from '../../../service/sn-site-metrics.service';

@Component({
  selector: 'sn-site-metrics-top-terms-page',
  templateUrl: './sn-site-metrics-top-terms-page.component.html'
})
export class TurSNSiteMetricsTopTermsPageComponent {
  private turSNSiteMetricsTopTerms: Observable<TurSNSiteMetricsTerm>;
  private siteId: string;
  private currentSiteId: string;
  private period: string;
  private currentPeriod: string;
  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteMetricsService: TurSNSiteMetricsService,
    private route: ActivatedRoute) {
    this.siteId = this.route.parent?.parent?.parent?.snapshot.paramMap.get('id') || "";
    this.period = this.route.snapshot.paramMap.get('period') || "this-month";
    this.currentPeriod = this.period;
    this.currentSiteId = this.siteId;
    this.turSNSiteMetricsTopTerms = this.getTopTermsService();
    if (this.period == 'today') {
      this.turSNSiteMetricsTopTerms = this.turSNSiteMetricsService.topTermsToday(this.siteId, 50);
    } else if (this.period == 'this-week') {
      this.turSNSiteMetricsTopTerms = this.turSNSiteMetricsService.topTermsThisWeek(this.siteId, 50);
    } else if (this.period == 'all-time') {
      this.turSNSiteMetricsTopTerms = this.turSNSiteMetricsService.topTermsAllTime(this.siteId, 50);
    } else {
      this.turSNSiteMetricsTopTerms = this.turSNSiteMetricsService.topTermsThisMonth(this.siteId, 50);
    }
  }

  getTopTermsService(): Observable<TurSNSiteMetricsTerm> {
    if (this.period == 'today') {
      return this.turSNSiteMetricsService.topTermsToday(this.siteId, 50);
    } else if (this.period == 'this-week') {
      return this.turSNSiteMetricsService.topTermsThisWeek(this.siteId, 50);
    } else if (this.period == 'all-time') {
      return this.turSNSiteMetricsService.topTermsAllTime(this.siteId, 50);
    } else {
      return this.turSNSiteMetricsService.topTermsThisMonth(this.siteId, 50);
    }
  }

  getId(): string {
    return this.siteId;
  }

  getTurSNSiteMetricsTopTerms(): Observable<TurSNSiteMetricsTerm> {
    this.siteId = this.route.parent?.parent?.parent?.snapshot.paramMap.get('id') || "";
    this.period = this.route.snapshot.paramMap.get('period') || "this-month";
    if (this.currentPeriod != this.period || this.currentSiteId != this.siteId) {
      this.currentPeriod = this.period;
      this.currentSiteId = this.siteId;
      this.turSNSiteMetricsTopTerms = this.getTopTermsService();
    }
    return this.turSNSiteMetricsTopTerms;
  }
}
