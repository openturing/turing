import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurStoreInstance } from '../../model/store-instance.model';
import { NotifierService } from 'angular-notifier-updated';
import { ActivatedRoute, Router } from '@angular/router';
import { TurStoreVendor } from '../../model/store-vendor.model';
import { TurLocale } from '../../../locale/model/locale.model';
import { UntypedFormControl, Validators } from '@angular/forms';
import {TurStoreInstanceService} from "../../service/store-instance.service";
import {TurLocaleService} from "../../../locale/service/locale.service";
import {TurStoreVendorService} from "../../service/store-vendor.service";
import {TurLLMInstance} from "../../../llm/model/llm-instance.model";

@Component({
    selector: 'store-instance-page',
    templateUrl: './store-instance-page.component.html',
    standalone: false
})
export class TurStoreInstancePageComponent implements OnInit {
  @ViewChild('modalDelete')
  modalDelete!: ElementRef;
  private turStoreInstance: Observable<TurStoreInstance>;
  private turLocales: Observable<TurLocale[]>;
  private turStoreVendors: Observable<TurStoreVendor[]>;
  private newObject: boolean = false;

  portControl = new UntypedFormControl(80, [Validators.max(100), Validators.min(0)])


  constructor(
    private readonly notifier: NotifierService,
    private turStoreInstanceService: TurStoreInstanceService,
    private turLocaleService: TurLocaleService,
    private turStoreVendorService: TurStoreVendorService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {

    this.turStoreVendors = turStoreVendorService.query();

    this.turLocales = turLocaleService.query();

    let id: string = this.activatedRoute.snapshot.paramMap.get('id') || "";

    this.newObject = (id != null && id.toLowerCase() === 'new');

    this.turStoreInstance = this.newObject ? this.turStoreInstanceService.getStructure() : this.turStoreInstanceService.get(id);
  }

  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create embedding store instance" : "Update embedding store instance";
  }

  getTurStoreInstance(): Observable<TurStoreInstance> {
    return this.turStoreInstance;
  }

  getTurStoreVendors(): Observable<TurStoreVendor[]> {

    return this.turStoreVendors;
  }


  getTurLocales(): Observable<TurLocale[]> {

    return this.turLocales;
  }

  getDefaults(_turStoreInstance: TurStoreInstance) {
    if (_turStoreInstance.turStoreVendor.id == 'CHROMA') {
      _turStoreInstance.url = "http://localhost:8000";
    }
  }

  ngOnInit(): void {
  }

  public save(_turStoreInstance: TurStoreInstance) {
    this.turStoreInstanceService.save(_turStoreInstance, this.newObject).subscribe(
      (turStoreInstance: TurStoreInstance) => {
        let message: string = this.newObject ? " Embedding store instance was created." : " Embedding store instance was updated.";

        _turStoreInstance = turStoreInstance;

        this.notifier.notify("success", turStoreInstance.title.concat(message));

        this.router.navigate(['/store/instance']);
      },
      response => {
        this.notifier.notify("error", "Embedding store instance was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });
  }

  public delete(_turStoreInstance: TurStoreInstance) {
    this.turStoreInstanceService.delete(_turStoreInstance).subscribe(
      () => {
        this.notifier.notify("success", _turStoreInstance.title.concat(" Embedding store instance was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");
        this.router.navigate(['/store/instance']);
      },
      response => {
        this.notifier.notify("error", "Embedding store instance was error: " + response);
      });
  }
}
