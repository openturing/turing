import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'sn-site-ai-root-page',
  templateUrl: './sn-site-ai-root-page.component.html',
  standalone: false
})
export class TurSNSiteAIRootPageComponent {
  constructor(private route: ActivatedRoute) {
  }
}
