import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {Observable} from 'rxjs';
import {NotifierService} from 'angular-notifier-updated';
import {ActivatedRoute, Router} from '@angular/router';
import {UntypedFormControl, Validators} from '@angular/forms';

import {TurLocale} from "../../../../locale/model/locale.model";
import {TurIntegrationWcSource} from "../../../model/integration-wc-source.model";
import {TurIntegrationWcSourceService} from "../../../service/integration-wc-source.service";
import {TurLocaleService} from "../../../../locale/service/locale.service";

@Component({
  selector: 'integration-wc-page',
  templateUrl: './integration-wc-page.component.html'
})

export class TurIntegrationWcPageComponent implements OnInit {
  @ViewChild('modalDelete')
  modalDelete!: ElementRef;
  private turIntegrationWcSource: Observable<TurIntegrationWcSource>;
  private newObject: boolean = false;
  private integrationId: string;
  private turLocales: Observable<TurLocale[]>;
  portControl = new UntypedFormControl(80, [Validators.max(100),
    Validators.min(0)])


  constructor(
    private readonly notifier: NotifierService,
    private turIntegrationWcSourceService: TurIntegrationWcSourceService,
    private turLocaleService: TurLocaleService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.turLocales = turLocaleService.query();
    let id: string = this.activatedRoute.snapshot.paramMap.get('wcId') || "";
    this.integrationId = this.activatedRoute.parent?.snapshot.paramMap.get('id') || "";
    turIntegrationWcSourceService.setIntegrationId(this.integrationId);
    this.newObject = (id != null && id.toLowerCase() === 'new');

    this.turIntegrationWcSource = this.newObject ? this.turIntegrationWcSourceService.getStructure() :
      this.turIntegrationWcSourceService.get(id);
  }

  getIntegrationId(): string {
    return this.integrationId;
  }
  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create web crawler" : "Update web crawler";
  }

  getTurIntegrationWcSource(): Observable<TurIntegrationWcSource> {
    return this.turIntegrationWcSource;
  }


  ngOnInit(): void {
  }

  getTurLocales(): Observable<TurLocale[]> {
    return this.turLocales;
  }

  public save(_turIntegrationWcSource: TurIntegrationWcSource) {
    this.turIntegrationWcSourceService.save(_turIntegrationWcSource, this.newObject).subscribe(
      (turIntegrationWcSource: TurIntegrationWcSource) => {
        let message: string = this.newObject ? " Web crawler source was created." : " Web crawler source was updated.";

        _turIntegrationWcSource = turIntegrationWcSource;

        this.notifier.notify("success", turIntegrationWcSource.turSNSite.concat(message));

        this.router.navigate(['/integration', 'web-crawler', this.getIntegrationId(), 'instance']);
      },
      (response: string) => {
        this.notifier.notify("error", " web crawler source was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });
  }

  public delete(_turIntegrationWcSource: TurIntegrationWcSource) {
    this.turIntegrationWcSourceService.delete(_turIntegrationWcSource).subscribe(
      () => {
        this.notifier.notify("success", _turIntegrationWcSource.turSNSite.concat(" web crawler source was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");
        this.router.navigate(['/integration', 'web-crawler', this.getIntegrationId(), 'instance']);
      },
      (response: string) => {
        this.notifier.notify("error", "Web crawler source was error: " + response);
      });
  }

  protected readonly JSON = JSON;
}
