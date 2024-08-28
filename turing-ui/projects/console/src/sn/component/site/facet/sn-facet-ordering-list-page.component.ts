import {Component} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {NotifierService} from 'angular-notifier-updated';
import {Observable} from 'rxjs';
import {TurSNSiteService} from "../../../service/sn-site.service";
import {TurSNSiteField} from "../../../model/sn-site-field.model";
import {
  CdkDragDrop,
  moveItemInArray,
  transferArrayItem,
} from '@angular/cdk/drag-drop';

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
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.siteId = this.activatedRoute.parent?.parent?.snapshot.paramMap.get('id') || "";
    this.turSNSiteFacetedField = turSNSiteService.getFacetedFields(this.siteId);
    this.filterText = "";
  }

  drop(event: CdkDragDrop<TurSNSiteField[], any>) {
    if (event.previousContainer === event.container) {
      moveItemInArray(event.container.data, event.previousIndex, event.currentIndex);
    } else {
      transferArrayItem(
        event.previousContainer.data,
        event.container.data,
        event.previousIndex,
        event.currentIndex,
      );
    }
  }

  getId(): string {
    return this.siteId;
  }

  getTurSNFacetOrdering(): Observable<TurSNSiteField[]> {
    return this.turSNSiteFacetedField;
  }

  setOrdering(fields: TurSNSiteField[] ): boolean {
    fields.forEach((item, index) => {
      item.facetPosition = index + 1;
    });
    return true;
  }

  saveOrdering(snFacetedFields: TurSNSiteField[]) {
    this.turSNSiteService.saveFacetOrderingList(this.siteId, snFacetedFields).subscribe(
      (turSNSiteFields: TurSNSiteField[]) => {
        this.notifier.notify("success", "Fields were reordering.");
        this.router.navigate(['/sn/site/', this.siteId, 'facet-ordering', 'list']);
      },
      (response: string) => {
        this.notifier.notify("error", "Reordering was error: " + response);
      },
      () => {
        // The POST observable is now completed.
      });
  }
}
