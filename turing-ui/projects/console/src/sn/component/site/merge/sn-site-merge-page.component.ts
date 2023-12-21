import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSNSite } from '../../../model/sn-site.model';
import { NotifierService } from 'angular-notifier-updated';
import { TurSNSiteService } from '../../../service/sn-site.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TurSNSiteMerge } from '../../../model/sn-site-merge.model';
import { TurSNSiteMergeService } from '../../../service/sn-site-merge.service';
import { TurNLPInstanceService } from 'projects/console/src/nlp/service/nlp-instance.service';
import { TurNLPInstance } from 'projects/console/src/nlp/model/nlp-instance.model';
import { TurSNSiteMergeField } from '../../../model/sn-site-merge-field.model';

@Component({
  selector: 'sn-site-merge-page',
  templateUrl: './sn-site-merge-page.component.html'
})
export class TurSNSiteMergePageComponent implements OnInit {
  @ViewChild('modalDeleteMerge')
  public modalDelete!: ElementRef;
  @ViewChild('modalAddOverwrittenField')
  public modalAddOverwrittenField!: ElementRef;
  public fieldModal: string = "";

  private turSNSite: Observable<TurSNSite>;
  private turSNSiteMerge: Observable<TurSNSiteMerge>;
  private turNLPInstances: Observable<TurNLPInstance[]>;
  private siteId: string;
  private newObject: boolean = false;

  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private turNLPInstanceService: TurNLPInstanceService,
    private turSNSiteMergeService: TurSNSiteMergeService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.turNLPInstances = turNLPInstanceService.query();
    this.siteId = this.activatedRoute.parent?.parent?.snapshot.paramMap.get('id') || "";
    let mergeId = this.activatedRoute.snapshot.paramMap.get('mergeId') || "";
    this.newObject = (mergeId.toLowerCase() === 'new');

    this.turSNSite = this.turSNSiteService.get(this.siteId);

    this.turSNSiteMerge = this.newObject ? this.turSNSiteMergeService.getStructure(this.siteId) : this.turSNSiteMergeService.get(this.siteId, mergeId);
  }
  ngOnInit(): void {
    // Empty
  }

  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }

  getTurSNSiteMerge(): Observable<TurSNSiteMerge> {
    return this.turSNSiteMerge;
  }

  geTurNLPInstances(): Observable<TurNLPInstance[]> {

    return this.turNLPInstances;
  }

  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create merge provider" : "Update merge provider";
  }

  public save(_turSNSiteMerge: TurSNSiteMerge) {
    this.turSNSiteMergeService.save(_turSNSiteMerge, this.newObject).subscribe(
      (turSNSiteMerge: TurSNSiteMerge) => {
        let message: string = this.newObject ? " merge was created." : " merge was updated.";

        _turSNSiteMerge = turSNSiteMerge;

        this.notifier.notify("success", (turSNSiteMerge.providerFrom + " " + turSNSiteMerge.providerTo).concat(message));

        this.router.navigate(['/sn/site/', turSNSiteMerge.turSNSite.id, 'merge', 'list']);
      },
      response => {
        this.notifier.notify("error", "Semantic navigation site merge was error: " + response);
      },
      () => {
        // The POST observable is now completed.
      });
  }

  public delete(_turSNSiteMerge: TurSNSiteMerge) {
    this.turSNSiteMergeService.delete(_turSNSiteMerge).subscribe(
      (turSNSiteMerge: TurSNSiteMerge) => {
        this.notifier.notify("success", (_turSNSiteMerge.providerFrom + " " + _turSNSiteMerge.providerTo).concat(" merge was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");

        this.router.navigate(['/sn/site/', _turSNSiteMerge.turSNSite.id, 'merge', 'list']);
      },
      response => {
        this.notifier.notify("error", "Semantic navigation site merge was error: " + response);
      },
      () => {
        // The POST observable is now completed.
      });

  }

  public addField(_turSNSiteMerge: TurSNSiteMerge, fieldName: string) {
    const turSNSiteMergeField: TurSNSiteMergeField = {
      id: undefined,
      name: fieldName
    };
    _turSNSiteMerge.overwrittenFields.push(turSNSiteMergeField);
    this.fieldModal = "";
    this.modalAddOverwrittenField.nativeElement.removeAttribute("open");
  }

  public deleteField(_turSNSiteMerge: TurSNSiteMerge, turSNSiteMergeField: TurSNSiteMergeField) {
    _turSNSiteMerge.overwrittenFields = _turSNSiteMerge.overwrittenFields.filter(item => item !== turSNSiteMergeField);
  }
}
