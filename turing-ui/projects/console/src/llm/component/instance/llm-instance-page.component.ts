import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurLLMInstance } from '../../model/llm-instance.model';
import { NotifierService } from 'angular-notifier-updated';
import { ActivatedRoute, Router } from '@angular/router';
import { TurLLMVendor } from '../../model/llm-vendor.model';
import { TurLocale } from '../../../locale/model/locale.model';
import { UntypedFormControl, Validators } from '@angular/forms';
import {TurLLMInstanceService} from "../../service/llm-instance.service";
import {TurLocaleService} from "../../../locale/service/locale.service";
import {TurLLMVendorService} from "../../service/llm-vendor.service";

@Component({
    selector: 'llm-instance-page',
    templateUrl: './llm-instance-page.component.html',
    standalone: false
})
export class TurLLMInstancePageComponent implements OnInit {
  @ViewChild('modalDelete')
  modalDelete!: ElementRef;
  private turLLMInstance: Observable<TurLLMInstance>;
  private turLocales: Observable<TurLocale[]>;
  private turLLMVendors: Observable<TurLLMVendor[]>;
  private newObject: boolean = false;

  portControl = new UntypedFormControl(80, [Validators.max(100), Validators.min(0)])


  constructor(
    private readonly notifier: NotifierService,
    private turLLMInstanceService: TurLLMInstanceService,
    private turLocaleService: TurLocaleService,
    private turLLMVendorService: TurLLMVendorService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {

    this.turLLMVendors = turLLMVendorService.query();

    this.turLocales = turLocaleService.query();

    let id: string = this.activatedRoute.snapshot.paramMap.get('id') || "";

    this.newObject = (id != null && id.toLowerCase() === 'new');

    this.turLLMInstance = this.newObject ? this.turLLMInstanceService.getStructure() : this.turLLMInstanceService.get(id);
  }

  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create language model instance" : "Update language model instance";
  }

  getTurLLMInstance(): Observable<TurLLMInstance> {
    return this.turLLMInstance;
  }

  getTurLLMVendors(): Observable<TurLLMVendor[]> {

    return this.turLLMVendors;
  }


  getTurLocales(): Observable<TurLocale[]> {

    return this.turLocales;
  }

  getDefaults(_turLLMInstance: TurLLMInstance) {
    if (_turLLMInstance.turLLMVendor.id == 'OLLAMA') {
      _turLLMInstance.url = "http://localhost:11434";
      _turLLMInstance.modelName = "MISTRAL";
      _turLLMInstance.temperature = 0.8;
      _turLLMInstance.topK = 6;
      _turLLMInstance.supportedCapabilities = "RESPONSE_FORMAT_JSON_SCHEMA";
      _turLLMInstance.timeout = "PT60S";
    }
  }
  ngOnInit(): void {
  }

  public save(_turLLMInstance: TurLLMInstance) {
    this.turLLMInstanceService.save(_turLLMInstance, this.newObject).subscribe(
      (turLLMInstance: TurLLMInstance) => {
        let message: string = this.newObject ? " Language model instance was created." : " Language model instance was updated.";

        _turLLMInstance = turLLMInstance;

        this.notifier.notify("success", turLLMInstance.title.concat(message));

        this.router.navigate(['/llm/instance']);
      },
      response => {
        this.notifier.notify("error", "Language model instance was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });
  }

  public delete(_turLLMInstance: TurLLMInstance) {
    this.turLLMInstanceService.delete(_turLLMInstance).subscribe(
      () => {
        this.notifier.notify("success", _turLLMInstance.title.concat(" Language model was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");
        this.router.navigate(['/llm/instance']);
      },
      response => {
        this.notifier.notify("error", "Language model was error: " + response);
      });
  }
}
