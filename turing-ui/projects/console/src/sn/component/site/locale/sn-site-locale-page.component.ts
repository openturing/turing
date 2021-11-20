import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSNSite } from '../../../model/sn-site.model';
import { NotifierService } from 'angular-notifier';
import { TurSNSiteService } from '../../../service/sn-site.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TurSNSiteLocale } from '../../../model/sn-site-locale.model';
import { TurSNSiteLocaleService } from '../../../service/sn-site-locale.service';
import { TurLocale } from 'projects/console/src/locale/model/locale.model';
import { TurLocaleService } from 'projects/console/src/locale/service/locale.service';
import { TurNLPInstanceService } from 'projects/console/src/nlp/service/nlp-instance.service';
import { TurNLPInstance } from 'projects/console/src/nlp/model/nlp-instance.model';

@Component({
  selector: 'sn-site-locale-page',
  templateUrl: './sn-site-locale-page.component.html'
})
export class TurSNSiteLocalePageComponent implements OnInit {
  @ViewChild('modalDeleteLocale')
  modalDelete!: ElementRef;
  private turSNSite: Observable<TurSNSite>;
  private turSNSiteLocale: Observable<TurSNSiteLocale>;
  private turNLPInstances: Observable<TurNLPInstance[]>;
  private turLocales: Observable<TurLocale[]>;
  private siteId: string;
  private newObject: boolean = false;

  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private turNLPInstanceService: TurNLPInstanceService,
    private turLocaleService: TurLocaleService,
    private turSNSiteLocaleService: TurSNSiteLocaleService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.turLocales = turLocaleService.query();
    this.turNLPInstances = turNLPInstanceService.query();
    this.siteId = this.activatedRoute.parent?.parent?.snapshot.paramMap.get('id') || "";
    let localeId = this.activatedRoute.snapshot.paramMap.get('localeId') || "";
    this.newObject = (localeId.toLowerCase() === 'new');

    this.turSNSite = this.turSNSiteService.get(this.siteId);

    this.turSNSiteLocale = this.newObject ? this.turSNSiteLocaleService.getStructure(this.siteId) : this.turSNSiteLocaleService.get(this.siteId, localeId);
  }

  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }

  getTurSNSiteLocale(): Observable<TurSNSiteLocale> {
    return this.turSNSiteLocale;
  }

  ngOnInit(): void {
  }

  getTurLocales(): Observable<TurLocale[]> {

    return this.turLocales;
  }

  geTurNLPInstances(): Observable<TurNLPInstance[]> {

    return this.turNLPInstances;
  }

  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create site language" : "Update site language";
  }

  public save(_turSNSiteLocale: TurSNSiteLocale) {
    this.turSNSiteLocaleService.save(_turSNSiteLocale, this.newObject).subscribe(
      (turSNSiteLocale: TurSNSiteLocale) => {
        let message: string = this.newObject ? " locale was created." : " locale was updated.";

        _turSNSiteLocale = turSNSiteLocale;

        this.notifier.notify("success", turSNSiteLocale.language.concat(message));

        this.router.navigate(['/sn/site/', turSNSiteLocale.turSNSite.id, 'locale', 'list']);
      },
      response => {
        this.notifier.notify("error", "Semantic navigation site locale was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });
  }

  public delete(_turSNSiteLocale: TurSNSiteLocale) {
    this.turSNSiteLocaleService.delete(_turSNSiteLocale).subscribe(
      (turSNSiteLocale: TurSNSiteLocale) => {
        this.notifier.notify("success", _turSNSiteLocale.language.concat(" locale was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");

        this.router.navigate(['/sn/site/', _turSNSiteLocale.turSNSite.id, 'locale', 'list']);
      },
      response => {
        this.notifier.notify("error", "Semantic navigation site locale was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });

  }
}
