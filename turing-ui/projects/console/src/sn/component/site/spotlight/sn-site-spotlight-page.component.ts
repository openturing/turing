import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSNSite } from '../../../model/sn-site.model';
import { NotifierService } from 'angular-notifier';
import { TurSNSiteService } from '../../../service/sn-site.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TurSNSiteSpotlight } from '../../../model/sn-site-spotlight.model';
import { TurSNSiteSpotlightService } from '../../../service/sn-site-spotlight.service';
import { TurSNSiteLocaleService } from '../../../service/sn-site-locale.service';
import { TurSNSiteLocale } from '../../../model/sn-site-locale.model';

@Component({
  selector: 'sn-site-spotlight-page',
  templateUrl: './sn-site-spotlight-page.component.html'
})
export class TurSNSiteSpotlightPageComponent implements OnInit {
  @ViewChild('modalDeleteSpotlight')
  modalDelete!: ElementRef;
  private turSNSite: Observable<TurSNSite>;
  private turSNSiteLocales: Observable<TurSNSiteLocale[]>;
  private turSNSiteSpotlight: Observable<TurSNSiteSpotlight>;
  private siteId: string;
  private newObject: boolean = false;

  constructor(
    private readonly notifier: NotifierService,
    private turSNSiteService: TurSNSiteService,
    private turSNSiteLocaleService: TurSNSiteLocaleService,
    private turSNSiteSpotlightService: TurSNSiteSpotlightService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.siteId = this.activatedRoute.parent?.parent?.snapshot.paramMap.get('id') || "";
    let spotlightId = this.activatedRoute.snapshot.paramMap.get('spotlightId') || "";
    this.turSNSite = this.turSNSiteService.get(this.siteId);
    this.turSNSiteLocales = turSNSiteLocaleService.query(this.siteId);
    this.newObject = (spotlightId.toLowerCase() === 'new');
    this.turSNSiteSpotlight = this.newObject ? this.turSNSiteSpotlightService.getStructure(this.siteId) : this.turSNSiteSpotlightService.get(this.siteId, spotlightId);
  }

  getTurSNSite(): Observable<TurSNSite> {
    return this.turSNSite;
  }

  getTurSNSiteLocales(): Observable<TurSNSiteLocale[]> {

    return this.turSNSiteLocales;
  }

  getTurSNSiteSpotlight(): Observable<TurSNSiteSpotlight> {
    return this.turSNSiteSpotlight;
  }

  ngOnInit(): void {
    // Empty
  }

  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create spotlight" : "Update spotlight";
  }

  public save(_turSNSiteSpotlight: TurSNSiteSpotlight) {
    this.turSNSiteSpotlightService.save(this.siteId, _turSNSiteSpotlight, this.newObject).subscribe(
      (turSNSiteSpotlight: TurSNSiteSpotlight) => {
        let message: string = this.newObject ? " spotlight was created." : " spotlight was updated.";

        _turSNSiteSpotlight = turSNSiteSpotlight;

        this.notifier.notify("success", turSNSiteSpotlight.name.concat(message));

        this.router.navigate(['/sn/site/', this.siteId, 'spotlight', 'list']);
      },
      response => {
        this.notifier.notify("error", "Semantic navigation site spotlight was error: " + response);
      },
      () => {
        // The POST observable is now completed.
      });
  }

  public delete(_turSNSiteSpotlight: TurSNSiteSpotlight) {
    this.turSNSiteSpotlightService.delete(this.siteId, _turSNSiteSpotlight).subscribe(
      (turSNSiteSpotlight: TurSNSiteSpotlight) => {
        this.notifier.notify("success", _turSNSiteSpotlight.language.concat(" spotlight was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");

        this.router.navigate(['/sn/site/', this.siteId, 'spotlight', 'list']);
      },
      response => {
        this.notifier.notify("error", "Semantic navigation site locale was error: " + response);
      },
      () => {
        // The POST observable is now completed.
      });
  }
}
