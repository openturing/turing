import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { NotifierService } from 'angular-notifier';
import { Observable } from 'rxjs';
import { TurSNSiteMerge } from '../../../model/sn-site-merge.model';
import { TurSNSiteMergeService } from '../../../service/sn-site-merge.service';

@Component({
  selector: 'sn-site-merge-list-page',
  templateUrl: './sn-site-merge-list-page.component.html'
})
export class TurSNSiteMergeListPageComponent {
  private turSNSiteMerges: Observable<TurSNSiteMerge[]>;
  private siteId: string;

  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteMergeService: TurSNSiteMergeService,
    private route: ActivatedRoute) {
    this.siteId = this.route.parent?.parent?.snapshot.paramMap.get('id') || "";
    this.turSNSiteMerges = turSNSiteMergeService.query(this.siteId);
  }

  getId(): string {
    return this.siteId;
  }

  getTurSNSiteMerges(): Observable<TurSNSiteMerge[]> {
    return this.turSNSiteMerges;
  }
}
