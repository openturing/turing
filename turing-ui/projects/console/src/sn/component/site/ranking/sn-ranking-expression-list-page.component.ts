import {Component} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {NotifierService} from 'angular-notifier-updated';
import {Observable} from 'rxjs';
import {TurSNRankingExpression} from "../../../model/sn-ranking-expression.model";
import {TurSNRankingExpressionService} from "../../../service/sn-ranking-expression.service";

@Component({
    selector: 'sn-ranking-expression-list-page',
    templateUrl: './sn-ranking-expression-list-page.component.html',
    standalone: false
})
export class TurSNRankingExpressionListPageComponent {
  private turSNRankingExpressions: Observable<TurSNRankingExpression[]>;
  private siteId: string;
  filterText: string;

  constructor(
    private readonly notifier: NotifierService,
    private turSNRankingExpressionService: TurSNRankingExpressionService,
    private route: ActivatedRoute) {
    this.siteId = this.route.parent?.parent?.snapshot.paramMap.get('id') || "";
    this.turSNRankingExpressions = turSNRankingExpressionService.query(this.siteId);
    this.filterText = "";
  }

  getId(): string {
    return this.siteId;
  }

  getTurSNRankingExpressions(): Observable<TurSNRankingExpression[]> {
    return this.turSNRankingExpressions;
  }
}
