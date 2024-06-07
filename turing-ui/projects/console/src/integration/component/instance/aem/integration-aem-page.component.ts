import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { NotifierService } from 'angular-notifier-updated';
import { ActivatedRoute, Router } from '@angular/router';

import { UntypedFormControl, Validators } from '@angular/forms';
import {TurIntegrationInstance} from "../../../model/integration-instance.model";
import {TurIntegrationVendor} from "../../../model/integration-vendor.model";
import {TurIntegrationInstanceService} from "../../../service/integration-instance.service";
import {TurIntegrationVendorService} from "../../../service/integration-vendor.service";

@Component({
  selector: 'integration-instance-page',
  templateUrl: './integration-aem-page.component.html'
})
export class TurIntegrationAEMPageComponent implements OnInit {
  @ViewChild('modalDelete')
  modalDelete!: ElementRef;
  private turIntegrationInstance: Observable<TurIntegrationInstance>;
  private turIntegrationVendors: Observable<TurIntegrationVendor[]>;
  private newObject: boolean = false;

  portControl = new UntypedFormControl(80, [Validators.max(100), Validators.min(0)])


  constructor(
    private readonly notifier: NotifierService,
    private turIntegrationInstanceService: TurIntegrationInstanceService,
    private turIntegrationVendorService: TurIntegrationVendorService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {

    this.turIntegrationVendors = turIntegrationVendorService.query();

    let id: string = this.activatedRoute.snapshot.paramMap.get('id') || "";

    this.newObject = (id != null && id.toLowerCase() === 'new');

    this.turIntegrationInstance = this.newObject ? this.turIntegrationInstanceService.getStructure() : this.turIntegrationInstanceService.get(id);
  }

  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create integration instance" : "Update integration instance";
  }

  getTurIntegrationInstance(): Observable<TurIntegrationInstance> {
    return this.turIntegrationInstance;
  }

  getTurIntegrationVendors(): Observable<TurIntegrationVendor[]> {

    return this.turIntegrationVendors;
  }

  ngOnInit(): void {
  }

  public save(_turIntegrationInstance: TurIntegrationInstance) {
    this.turIntegrationInstanceService.save(_turIntegrationInstance, this.newObject).subscribe(
      (turIntegrationInstance: TurIntegrationInstance) => {
        let message: string = this.newObject ? " Integration instance was created." : " Integration instance was updated.";

        _turIntegrationInstance = turIntegrationInstance;

        this.notifier.notify("success", turIntegrationInstance.title.concat(message));

        this.router.navigate(['/integration/instance']);
      },
      response => {
        this.notifier.notify("error", "Integration instance was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });
  }

  public delete(_turIntegrationInstance: TurIntegrationInstance) {
    this.turIntegrationInstanceService.delete(_turIntegrationInstance).subscribe(
      () => {
        this.notifier.notify("success", _turIntegrationInstance.title.concat(" Integration instance was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");
        this.router.navigate(['/integration/instance']);
      },
      response => {
        this.notifier.notify("error", "Integration instance was error: " + response);
      });
  }
}
