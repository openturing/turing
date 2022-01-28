import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSNSite } from '../../../model/sn-site.model';
import { NotifierService } from 'angular-notifier';
import { TurSNSiteService } from '../../../service/sn-site.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TurSNSiteField } from '../../../model/sn-site-field.model';
import { TurSNFieldTypeService } from '../../../service/sn-field-type.service';
import { TurSNFieldType } from '../../../model/sn-field-type.model';

@Component({
  selector: 'sn-site-field-page',
  templateUrl: './sn-site-field-page.component.html'
})
export class TurSNSiteFieldPageComponent implements OnInit {
  @ViewChild('modalDeleteField')
  modalDelete!: ElementRef;

  private turSNSite: Observable<TurSNSite>;
  private turSNSiteField: Observable<TurSNSiteField>;
  private turSNFieldTypes: Observable<TurSNFieldType[]>;
  private siteId!: string;
  private newObject: boolean = false;

  constructor(private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private turSNFieldTypeService: TurSNFieldTypeService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.siteId = this.activatedRoute.parent?.parent?.snapshot.paramMap.get('id') || "";
    let fieldId: string = this.activatedRoute.snapshot.paramMap.get('fieldId') || "";
    this.newObject = (fieldId.toLowerCase() === 'new');

    this.turSNSite = this.turSNSiteService.get(this.siteId);
    this.turSNFieldTypes = this.turSNFieldTypeService.query();

    this.turSNSiteField = this.newObject ? this.turSNSiteService.getFieldStructure(this.siteId) : this.turSNSiteService.getField(this.siteId, fieldId);
  }

  getTurSNFieldTypes(): Observable<TurSNFieldType[]> {
    return this.turSNFieldTypes;
  }

  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }

  getTurSNSiteField(): Observable<TurSNSiteField> {
    return this.turSNSiteField;
  }

  ngOnInit(): void {
    // Empty
  }

  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create field" : "Update field";
  }

  public saveSiteField(_turSNSiteField: TurSNSiteField) {
    this.turSNSiteService.saveField(this.siteId, _turSNSiteField, this.newObject).subscribe(
      (turSNSiteField: TurSNSiteField) => {
        let message: string = this.newObject ? " field was created." : " field was updated.";

        _turSNSiteField = turSNSiteField;
        this.notifier.notify("success", turSNSiteField.name.concat(message));
      },
      (response: string) => {
        this.notifier.notify("error", "Field was error: " + response);
      },
      () => {
        // The POST observable is now completed.
      });
  }

  public delete(_turSNSiteField: TurSNSiteField) {
    this.turSNSiteService.deleteField(this.siteId, _turSNSiteField).subscribe(
      (turSNSiteField: TurSNSiteField) => {
        this.notifier.notify("success", _turSNSiteField.name.concat(" field was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");

        this.router.navigate(['/sn/site/', this.siteId, 'field', 'list']);
      },
      response => {
        this.notifier.notify("error", "Field was error: " + response);
      },
      () => {
        // The POST observable is now completed.
      });
  }
}
