import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'sn-ranking-expression-root-page',
    templateUrl: './sn-ranking-expression-root-page.component.html',
    standalone: false
})
export class TurSNRankingExpressionRootPageComponent {
  constructor(private route: ActivatedRoute) {
  }
}
