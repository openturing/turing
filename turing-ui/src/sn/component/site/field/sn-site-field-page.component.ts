import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSNSite } from 'src/sn/model/sn-site.model';
import { NotifierService } from 'angular-notifier';
import { TurSNSiteService } from 'src/sn/service/sn-site.service';
import { ActivatedRoute } from '@angular/router';
import { TurSNSiteField } from 'src/sn/model/sn-site-field.model';
import { TurSNFieldTypeService } from 'src/sn/service/sn-field-type.service';
import { TurSNFieldType } from 'src/sn/model/sn-field-type.model';

@Component({
  selector: 'sn-site-page',
  templateUrl: './sn-site-field-page.component.html'
})
export class TurSNSiteFieldPageComponent implements OnInit {
  private turSNSite: Observable<TurSNSite>;
  private turSNSiteField: Observable<TurSNSiteField>;
  private turSNFieldTypes: Observable<TurSNFieldType[]>;
  private siteId: string;

  constructor(private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private turSNFieldTypeService: TurSNFieldTypeService,
    private route: ActivatedRoute) {
    let siteId = this.route.parent.parent.snapshot.paramMap.get('id');
    let fieldId = this.route.snapshot.paramMap.get('fieldId');
    this.turSNSite = this.turSNSiteService.get(this.siteId);
    this.turSNSiteField = this.turSNSiteService.getField(this.siteId, fieldId);
    this.turSNFieldTypes = this.turSNFieldTypeService.query();
  }

  getTurSNFieldTypes(): Observable<TurSNFieldType[]> {
    return this.turSNFieldTypes;
  }

  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }

  getTurSNSiteField(): Observable<TurSNSite> {
    return this.turSNSiteField;
  }

  ngOnInit(): void {
  }

  public saveSiteField(_turSNSiteField: TurSNSiteField) {
    this.turSNSiteService.saveField(this.siteId, _turSNSiteField).subscribe(
      (turSNSiteField: TurSNSiteField) => {
        _turSNSiteField = turSNSiteField;
        this.notifier.notify("success", turSNSiteField.name.concat(" semantic navigation site field was updated."));
      },
      response => {
        this.notifier.notify("error", "SN site was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }
}
