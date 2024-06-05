import {Component, OnInit} from '@angular/core';
import {Observable} from 'rxjs';
import {TurSNSite} from '../../../model/sn-site.model';
import {NotifierService} from 'angular-notifier-updated';
import {TurSNSiteService} from '../../../service/sn-site.service';
import {ActivatedRoute} from '@angular/router';
import {TurSNSiteField} from '../../../model/sn-site-field.model';
import {TurSNStatusFields} from "../../../model/sn-field-status.model";
import {TurSNFieldCheck} from "../../../model/sn-field-check.model";

@Component({
  selector: 'sn-site-page',
  templateUrl: './sn-site-field-list-page.component.html'
})
export class TurSNSiteFieldListPageComponent implements OnInit {
  private readonly turSNSite: Observable<TurSNSite>;
  private readonly turSNSiteSEFields: Observable<TurSNSiteField[]>;
  private readonly turSNSiteNLPFields: Observable<TurSNSiteField[]>;
  private readonly turSNSiteStatusFields: Observable<TurSNStatusFields>;
  private readonly siteId: string;
  filterCustomField: string;
  filterNLPField: string;
  protected readonly turSNFieldCheckEmpty!: TurSNFieldCheck;

  constructor(private readonly notifier: NotifierService,
              private turSNSiteService: TurSNSiteService,
              private route: ActivatedRoute) {
    this.siteId = this.route.parent?.parent?.snapshot.paramMap.get('id') || "";
    this.turSNSiteSEFields = turSNSiteService.getFieldsByType(this.siteId, "se");
    this.turSNSiteNLPFields = turSNSiteService.getFieldsByType(this.siteId, "ner");
    this.turSNSiteStatusFields = turSNSiteService.getStatusFields(this.siteId);
    this.turSNSite = this.turSNSiteService.get(this.siteId);
    this.filterCustomField = "";
    this.filterNLPField = "";
  }

  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }

  getTurSNSiteSEFields(): Observable<TurSNSiteField[]> {
    return this.turSNSiteSEFields;
  }

  getTurSNSiteNLPFields(): Observable<TurSNSiteField[]> {
    return this.turSNSiteNLPFields;
  }

  getTurSNSiteStatusFields(): Observable<TurSNStatusFields> {
    return this.turSNSiteStatusFields;
  }

  getTurSNSiteStatusField(statusFields: TurSNStatusFields, id: string): TurSNFieldCheck {

    let turSNFieldCheck: TurSNFieldCheck | undefined = statusFields.fields.find(x => x.id == id);
    if (turSNFieldCheck != undefined) {
      return turSNFieldCheck;
    }
    return this.turSNFieldCheckEmpty;
  }


  getId(): string {
    return this.siteId;
  }

  ngOnInit(): void {
    //Empty
  }

  public updateField(_turSNSiteField: TurSNSiteField, fieldName: string, event: Event) {
    (_turSNSiteField[fieldName as keyof TurSNSiteField] as any) = event ? 1 : 0;
    this.turSNSiteService.saveField(this.siteId, _turSNSiteField, false).subscribe(
      (turSNSiteField: TurSNSiteField) => {
        _turSNSiteField = turSNSiteField;
        this.notifier.notify("success", turSNSiteField.name.concat(" semantic navigation field was updated."));
      },
      (response: string) => {
        this.notifier.notify("error", "Semantic navigation field has a error: " + response);
      },
      () => {
        // The POST observable is now completed.
      });
  }
}
