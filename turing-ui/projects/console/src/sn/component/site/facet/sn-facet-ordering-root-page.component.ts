import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import {TurSNFacetOrderingListPageComponent} from "./sn-facet-ordering-list-page.component";

@Component({
  selector: 'sn-facet-ordering-root-page',
  templateUrl: './sn-facet-ordering-root-page.component.html'
})
export class TurSNFacetOrderingRootPageComponent {
  constructor(private route: ActivatedRoute) {
  }
}
