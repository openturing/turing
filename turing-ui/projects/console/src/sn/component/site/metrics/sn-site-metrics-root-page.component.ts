import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'sn-site-metrics-root-page',
  templateUrl: './sn-site-metrics-root-page.component.html'
})
export class TurSNSiteMetricsRootPageComponent {
  constructor(private route: ActivatedRoute) {
  }
}
