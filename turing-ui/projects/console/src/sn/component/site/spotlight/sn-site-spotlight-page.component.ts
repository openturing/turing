import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import {Observable} from 'rxjs';
import { NotifierService } from 'angular-notifier-updated';
import {ActivatedRoute, Router} from '@angular/router';
import { TurSNSiteSpotlight } from '../../../model/sn-site-spotlight.model';
import { TurSNSiteSpotlightService } from '../../../service/sn-site-spotlight.service';
import { TurSNSiteLocaleService } from '../../../service/sn-site-locale.service';
import { TurSNSiteLocale } from '../../../model/sn-site-locale.model';
import {TurSNSiteSpotlightTerm} from "../../../model/sn-site-spotlight-term.model";
import {TurSNSiteSpotlightDocument} from "../../../model/sn-site-spotlight-document.model";
import {TurSNSearchService} from "../../../../../../sn/src/search/service/sn-search.service";
import {TurSNSearch} from "../../../../../../sn/src/search/model/sn-search.model";
import {TurSNSearchDocument} from "../../../../../../sn/src/search/model/sn-search-document.model";

@Component({
  selector: 'sn-site-spotlight-page',
  templateUrl: './sn-site-spotlight-page.component.html'
})
export class TurSNSiteSpotlightPageComponent implements OnInit {
  @ViewChild('modalDeleteSpotlight')
  modalDelete!: ElementRef;
  @ViewChild('modalSelectDocument')
  public modalSelectDocument!: ElementRef;
  private readonly turSNSiteLocales: Observable<TurSNSiteLocale[]>;
  private readonly turSNSiteSpotlight: Observable<TurSNSiteSpotlight>;
  public turSNSearchResults: Observable<TurSNSearch> = new Observable<TurSNSearch>();
  private readonly siteId: string;
  private readonly newObject: boolean = false;
  public inputSearchDocument: string;

  constructor(
    private turSNSearchService: TurSNSearchService,
    private readonly notifier: NotifierService,
    private turSNSiteLocaleService: TurSNSiteLocaleService,
    private turSNSiteSpotlightService: TurSNSiteSpotlightService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {
    this.siteId = this.activatedRoute.parent?.parent?.snapshot.paramMap.get('id') || "";
    let spotlightId = this.activatedRoute.snapshot.paramMap.get('spotlightId') || "";
    this.turSNSiteLocales = this.turSNSiteLocaleService.query(this.siteId);
    this.newObject = (spotlightId.toLowerCase() === 'new');
    this.turSNSiteSpotlight = this.newObject ? this.turSNSiteSpotlightService.getStructure(this.siteId) :
      this.turSNSiteSpotlightService.get(this.siteId, spotlightId);
    this.inputSearchDocument = "";
  }

  getSearchResult(siteName: string, query: string, page: string, locale: string): Observable<TurSNSearch> {
    let  turSort: string = "title:desc";
    let  turFilterQuery!: string[];
    let  turTargetingRule!: string[];
    let  turAutoCorrectionDisabled!: string;

    return this.turSNSearchService.query(
      siteName,
      query,
      page,
      locale,
      turSort,
      turFilterQuery,
      turTargetingRule,
      turAutoCorrectionDisabled);
  }

  camelize(str: string): string {
    return str.replace(/^\w|[A-Z]|\b\w|\s+/g, function (match, index) {
      if (+match === 0) return "";
      return index === 0 ? match.toUpperCase() : match.toLowerCase();
    });
  }

  public addDocument(_turSNSiteSpotlight: TurSNSiteSpotlight, turSNSearchDocument: TurSNSearchDocument) {
    let turSNSiteSpotlightDocument = new TurSNSiteSpotlightDocument() ;
    turSNSiteSpotlightDocument.id = undefined;
    turSNSiteSpotlightDocument.title = turSNSearchDocument.fields.title;
    turSNSiteSpotlightDocument.type = turSNSearchDocument.fields.type;
    turSNSiteSpotlightDocument.position = 1;
    turSNSiteSpotlightDocument.link = turSNSearchDocument.fields.url;
    turSNSiteSpotlightDocument.content = turSNSearchDocument.fields.abstract;
    turSNSiteSpotlightDocument.referenceId = "TURING";
    _turSNSiteSpotlight.turSNSiteSpotlightDocuments.push(turSNSiteSpotlightDocument);

    this.inputSearchDocument = "";
    this.modalSelectDocument.nativeElement.removeAttribute("open");
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

  newTerm(turSNSiteSpotlightTerms: TurSNSiteSpotlightTerm[]) {
    turSNSiteSpotlightTerms.push(new TurSNSiteSpotlightTerm());

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

  removeTerm(snSiteSpotlight: TurSNSiteSpotlight, snSiteSpotlightTerm: TurSNSiteSpotlightTerm) {
    snSiteSpotlight.turSNSiteSpotlightTerms =
      snSiteSpotlight.turSNSiteSpotlightTerms
        .filter(term => term != snSiteSpotlightTerm);
  }

  removeDocument(snSiteSpotlight: TurSNSiteSpotlight, snSiteSpotlightDocument: TurSNSiteSpotlightDocument) {
    snSiteSpotlight.turSNSiteSpotlightDocuments =
      snSiteSpotlight.turSNSiteSpotlightDocuments
        .filter(document => document != snSiteSpotlightDocument);
  }

  searchDocument(snSiteSpotlight: TurSNSiteSpotlight, pageNumber: number) {
    this.turSNSearchResults = this.getSearchResult(snSiteSpotlight.turSNSite.name,
      this.inputSearchDocument, pageNumber.toString(), snSiteSpotlight.language);
  }
}
