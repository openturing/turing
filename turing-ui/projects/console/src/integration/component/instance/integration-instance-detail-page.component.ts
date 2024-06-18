import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurIntegrationInstance } from '../../model/integration-instance.model';
import { NotifierService } from 'angular-notifier-updated';
import { TurIntegrationInstanceService } from '../../service/integration-instance.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TurIntegrationVendor } from '../../model/integration-vendor.model';
import { TurIntegrationVendorService } from '../../service/integration-vendor.service';
import { UntypedFormControl, Validators } from '@angular/forms';

@Component({
  selector: 'integration-instance-detail-page',
  templateUrl: './integration-instance-detail-page.component.html'
})
export class TurIntegrationInstanceDetailPageComponent implements OnInit {
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

    let id: string = this.activatedRoute.parent?.snapshot.paramMap.get('id') || "";

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
}
