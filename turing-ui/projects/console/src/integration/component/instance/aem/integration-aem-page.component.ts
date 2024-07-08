import {Component, ElementRef, OnInit, ViewChild} from '@angular/core';
import {Observable} from 'rxjs';
import {NotifierService} from 'angular-notifier-updated';
import {ActivatedRoute, Router} from '@angular/router';
import {UntypedFormControl, Validators} from '@angular/forms';
import {TurIntegrationAemSource} from "../../../model/integration-aem-source.model";
import {TurIntegrationAemSourceService} from "../../../service/integration-aem-source.service";
import 'brace';

import 'brace/mode/text';
import 'brace/theme/github';

import 'brace/theme/clouds';
import 'brace/mode/javascript';

import 'brace/mode/json';
import {AceComponent, AceConfigInterface, AceDirective} from "ngx-ace-wrapper";
import {TurSNRankingCondition} from "../../../../sn/model/sn-ranking-condition.model";
import {TurSNRankingExpression} from "../../../../sn/model/sn-ranking-expression.model";
import {TurIntegrationAemLocalePath} from "../../../model/integration-aem-locale-path.model";
import {TurLocale} from "../../../../locale/model/locale.model";
import {TurLocaleService} from "../../../../locale/service/locale.service";

@Component({
  selector: 'integration-aem-page',
  templateUrl: './integration-aem-page.component.html'
})

export class TurIntegrationAemPageComponent implements OnInit {
  public config: AceConfigInterface = {
    mode: 'ace/mode/json',
    theme: 'github',
    readOnly: false,
  };
  @ViewChild(AceComponent, {static: false})
  componentRef?: AceComponent;
  @ViewChild(AceDirective, {static: false})
  directiveRef?: AceDirective;
  @ViewChild('modalDelete')
  modalDelete!: ElementRef;
  private turIntegrationAemSource: Observable<TurIntegrationAemSource>;
  private newObject: boolean = false;
  private integrationId: string;
  private turLocales: Observable<TurLocale[]>;
  portControl = new UntypedFormControl(80, [Validators.max(100), Validators.min(0)])


  constructor(
    private readonly notifier: NotifierService,
    private turIntegrationAemSourceService: TurIntegrationAemSourceService,
    private turLocaleService: TurLocaleService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.config.mode = 'json';
    this.config.tabSize = 2;
    this.config.wrap = true;
    this.turLocales = turLocaleService.query();
    let id: string = this.activatedRoute.snapshot.paramMap.get('aemId') || "";
    this.integrationId = this.activatedRoute.parent?.parent?.snapshot.paramMap.get('id') || "";
    turIntegrationAemSourceService.setIntegrationId(this.integrationId);
    this.newObject = (id != null && id.toLowerCase() === 'new');

    this.turIntegrationAemSource = this.newObject ? this.turIntegrationAemSourceService.getStructure() :
      this.turIntegrationAemSourceService.get(id);
  }

  public toggleMode(_turIntegrationAemSource: TurIntegrationAemSource): void {
    _turIntegrationAemSource.mappingJson = _turIntegrationAemSource.mappingJson.replace(/,/g, ',\n');
  }
  getIntegrationId(): string {
    return this.integrationId;
  }
  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create AEM source" : "Update AEM source";
  }

  getTurIntegrationAemSource(): Observable<TurIntegrationAemSource> {
    return this.turIntegrationAemSource;
  }


  ngOnInit(): void {
  }

  newLocale(turIntegrationAemLocalePaths: TurIntegrationAemLocalePath[]) {
    let turIntegrationAemLocalePath: TurIntegrationAemLocalePath = new TurIntegrationAemLocalePath();
    turIntegrationAemLocalePaths.push(turIntegrationAemLocalePath);

  }

  removeLocale(turIntegrationAemSource: TurIntegrationAemSource, turIntegrationAemLocalePath: TurIntegrationAemLocalePath) {
    turIntegrationAemSource.localePaths =
      turIntegrationAemSource.localePaths.filter(condition =>
        condition != turIntegrationAemLocalePath)
  }

  getTurLocales(): Observable<TurLocale[]> {
    return this.turLocales;
  }

  public save(_turIntegrationAemSource: TurIntegrationAemSource) {
    this.turIntegrationAemSourceService.save(_turIntegrationAemSource, this.newObject).subscribe(
      (turIntegrationAemSource: TurIntegrationAemSource) => {
        let message: string = this.newObject ? " Integration AEM source was created." : " Integration AEM source was updated.";

        _turIntegrationAemSource = turIntegrationAemSource;

        this.notifier.notify("success", turIntegrationAemSource.group.concat(message));

        this.router.navigate(['/integration/instance', this.getIntegrationId(), 'aem']);
      },
      (response: string) => {
        this.notifier.notify("error", "Integration AEM source was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });
  }

  public delete(_turIntegrationAemSource: TurIntegrationAemSource) {
    this.turIntegrationAemSourceService.delete(_turIntegrationAemSource).subscribe(
      () => {
        this.notifier.notify("success", _turIntegrationAemSource.group.concat(" Integration AEM source was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");
        this.router.navigate(['/integration/instance', this.getIntegrationId(), 'aem']);
      },
      (response: string) => {
        this.notifier.notify("error", "Integration AEM source was error: " + response);
      });
  }

  protected readonly JSON = JSON;
}
