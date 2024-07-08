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
  selector: 'integration-instance-page',
  templateUrl: './integration-instance-page.component.html'
})
export class TurIntegrationInstancePageComponent implements OnInit {
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

  getTurIntegrationInstance(): Observable<TurIntegrationInstance> {
    return this.turIntegrationInstance;
  }

  ngOnInit(): void {
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
