import {Component} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {NotifierService} from 'angular-notifier-updated';
import {Observable} from 'rxjs';
import {TurSNSiteService} from "../../../service/sn-site.service";
import {TurSNSiteField} from "../../../model/sn-site-field.model";

@Component({
  selector: 'sn-facet-ordering-list-page',
  templateUrl: './sn-facet-ordering-list-page.component.html'
})
export class TurSNFacetOrderingListPageComponent {
  private turSNSiteFacetedField: Observable<TurSNSiteField[]>;
  private readonly siteId: string;
  filterText: string;

  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private route: ActivatedRoute) {
    this.siteId = this.route.parent?.parent?.snapshot.paramMap.get('id') || "";
    this.turSNSiteFacetedField = turSNSiteService.getFacetedFields(this.siteId);
    this.filterText = "";
  }

  getId(): string {
    return this.siteId;
  }

  getTurSNFacetOrdering(): Observable<TurSNSiteField[]> {
    return this.turSNSiteFacetedField;
  }
}
