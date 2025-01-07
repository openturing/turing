import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'sn-site-spotlight-root-page',
    templateUrl: './sn-site-spotlight-root-page.component.html',
    standalone: false
})
export class TurSNSiteSpotlightRootPageComponent {
  constructor(private route: ActivatedRoute) {
  }
}
