import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, NavigationExtras, Params, Router, RouterModule } from '@angular/router';
import { Location, LocationStrategy, PathLocationStrategy, PlatformLocation } from '@angular/common';
import { Observable } from 'rxjs';
import { TurSNSearch } from '../../model/sn-search.model';
import { TurSNSearchService } from '../../service/sn-search.service';
import {TurSNChat} from "../../model/sn-chat.model";

@Component({
    selector: 'search-root-page',
    providers: [Location, { provide: LocationStrategy, useClass: PathLocationStrategy }],
    templateUrl: './search-root-page.component.html',
    standalone: false
})

export class TurSNSearchRootPageComponent implements OnInit {
  private turSNSearchItems: Observable<TurSNSearch>;
  public chat!: Observable<TurSNChat>;
  public turSiteName!: string;
  public turQuery!: string;
  private turPage!: string;
  private turLocale!: string;
  public turSort!: string;
  private turFilterQuery!: string[];
  private turTargetingRule!: string[];
  private turAutoCorrectionDisabled!: string;
  public sortOptions: Map<string, string> = new Map();
  private autoCompleteList!: Observable<string[]>;

  constructor(
    private turSNSearchService: TurSNSearchService,
    private router: Router,
    private activatedRoute: ActivatedRoute,
    private platformLocation: PlatformLocation) {
    this.sortOptions.set("relevance", "Relevance");
    this.sortOptions.set("newest", "Newest");
    this.sortOptions.set("oldest", "Oldest");
    this.updateParameters(platformLocation.pathname);

    this.chat = turSNSearchService.chat(this.turQuery);
    this.turSNSearchItems = turSNSearchService.query(
      this.turSiteName,
      this.turQuery,
      this.turPage,
      this.turLocale,
      this.turSort,
      this.turFilterQuery,
      this.turTargetingRule,
      this.turAutoCorrectionDisabled);
  }

  generateQueryString(): string {
    this.turPage = "1";

    return TurSNSearchService.generateQueryString(this.turQuery, this.turPage, this.turLocale, this.turSort,
        this.turFilterQuery, this.turTargetingRule, this.turAutoCorrectionDisabled);
  }
  getTurSNSearchItems(): Observable<TurSNSearch> {
    return this.turSNSearchItems;
  }
  retrieveAutoComplete(): void {
    this.autoCompleteList = this.turSNSearchService.autoComplete(
      this.turSiteName,
      this.turQuery,
      this.turPage,
      this.turLocale,
      this.turSort,
      this.turFilterQuery,
      this.turTargetingRule,
      this.turAutoCorrectionDisabled);
  }
  getChat(): Observable<TurSNChat> {
    return this.chat;
  }
  getAutoComplete(): Observable<string[]> {
    return this.autoCompleteList;
  }
  updateParameters(turPath: string) {
    if (turPath.endsWith("/")) {
      turPath = turPath.substring(0, turPath.length - 1);
    }

    let turSiteNameSplit = turPath.split('/');
    let turForceSiteName = this.activatedRoute.snapshot.queryParams["_setsite"];
    if (turForceSiteName != null) {
      this.turSiteName = turForceSiteName;
    }
    else {
      this.turSiteName = turSiteNameSplit[turSiteNameSplit.length - 1];
    }
    this.turQuery = this.activatedRoute.snapshot.queryParams["q"] || "*";
    this.turPage = this.activatedRoute.snapshot.queryParams["p"] || "1";
    this.turLocale = this.activatedRoute.snapshot.queryParams["_setlocale"];
    this.turSort = this.activatedRoute.snapshot.queryParams["sort"] || "relevance";
    this.turFilterQuery = this.activatedRoute.snapshot.queryParams["fq[]"];
    this.turTargetingRule = this.activatedRoute.snapshot.queryParams["tr[]"];
    this.turAutoCorrectionDisabled = this.activatedRoute.snapshot.queryParams["nfpr"];
  }

  showAll() {
    this.turQuery = "*";
    this.turRedirect("?" + this.generateQueryString());
  }
  turRedirect(href: string) {
    let result: { [key: string]: string[] } = {};

    new URLSearchParams(href.split('?')[1]).forEach(function (value, key) {
      console.log(key);
      if (result.hasOwnProperty(key)) {
        result[key].splice(0, 0, value);
      }
      else {
        result[key] = [value];
      }
    });
    let objToSend: NavigationExtras = {
      queryParams: result
    };

    this.router.navigate(["/"], objToSend).then(() => {
      window.location.reload();
    });
  }
  changeOrderBy(orderBy: string) {
    this.turSort = orderBy;
    this.searchIt();
  }

  changeLocale(locale: string) {
    this.turLocale = locale;
    this.searchIt();
  }

  searchIt() {
    this.turRedirect("?" + this.generateQueryString());
  }

  camelize(str: string): string {
    return str.replace(/^\w|[A-Z]|\b\w|\s+/g, function (match, index) {
      if (+match === 0) return ""; // or if (/\s+/.test(match)) for space
      return index === 0 ? match.toUpperCase() : match.toLowerCase();
    });
  }

  currentDate(): number {
    return (new Date()).getFullYear();
  }
  ngOnInit(): void {
  }

  title = 'sn';
}
