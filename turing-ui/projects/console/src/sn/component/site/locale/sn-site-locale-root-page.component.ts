import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
    selector: 'sn-site-locale-root-page',
    templateUrl: './sn-site-locale-root-page.component.html',
    standalone: false
})
export class TurSNSiteLocaleRootPageComponent {
  constructor(private route: ActivatedRoute) {
  }
}
