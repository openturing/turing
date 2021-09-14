import { Component, OnInit } from '@angular/core';
import { NotifierService } from 'angular-notifier';
import { ActivatedRoute, NavigationExtras, Params, Router, RouterModule } from '@angular/router';
import { Location, LocationStrategy, PathLocationStrategy } from '@angular/common';
import { Observable } from 'rxjs';
import { TurSNSearch } from '../../model/sn-search.model';
import { TurSNSearchService } from '../../service/sn-search.service';
import { HttpParams } from '@angular/common/http';

@Component({
  selector: 'search-root-page',
  providers: [Location, { provide: LocationStrategy, useClass: PathLocationStrategy }],
  templateUrl: './search-root-page.component.html'
})

export class TurSNSearchRootPageComponent implements OnInit {
  private turSNSearchItems: Observable<TurSNSearch>;
  private turSiteName!: string;
  private turQueryString!: string;
  private turQuery!: string;
  private turPage!: string;
  private turLocale!: string;
  public turSort!: string;
  private turFilterQuery!: string[];
  private turTargetingRule!: string[];
  public sortOptions: Map<string, string> = new Map();

  constructor(
    private turSNSearchService: TurSNSearchService,
    private router: Router,
    private location: Location,
    private activatedRoute: ActivatedRoute) {
    this.sortOptions.set("relevance", "Relevance");
    this.sortOptions.set("newest", "Newest");
    this.sortOptions.set("oldest", "Oldest");

    this.updateParameters();

    this.turSNSearchItems = turSNSearchService.query(
      this.turSiteName,
      this.turQuery,
      this.turPage,
      this.turLocale,
      this.turSort,
      this.turFilterQuery,
      this.turTargetingRule);
  }

  getTurSNSearchItems(): Observable<TurSNSearch> {
    return this.turSNSearchItems;
  }

  updateParameters() {
    let turPath: string = this.router.url.split('?')[0];
    if (turPath.endsWith("/")) {
      turPath = turPath.substring(0, turPath.length - 1);
    }

    let turSiteNameSplit = turPath.split('/');
    this.turSiteName = turSiteNameSplit[turSiteNameSplit.length - 1];



    this.turQuery = this.activatedRoute.snapshot.queryParams["q"] || "*";
    this.turPage = this.activatedRoute.snapshot.queryParams["p"] || "1";
    this.turLocale = this.activatedRoute.snapshot.queryParams["_setlocale"];
    this.turSort = this.activatedRoute.snapshot.queryParams["sort"] || "relevance";
    this.turFilterQuery = this.activatedRoute.snapshot.queryParams["fq[]"];
    this.turTargetingRule = this.activatedRoute.snapshot.queryParams["tr[]"];

    this.turQueryString = TurSNSearchService.generateQueryString(this.turQuery, this.turPage, this.turLocale, this.turSort, this.turFilterQuery, this.turTargetingRule);

    console.log(this.router.url);
    console.log(this.turQueryString);
    console.log(this.turQuery);
    console.log(this.turPage);
    console.log(this.turLocale);
    console.log(this.turSort);
    console.log(this.turFilterQuery);
    console.log(this.turTargetingRule);

  }


  turRedirect(href: string) {
    let result: { [key: string]: string } = {};
    new URLSearchParams(href.split('?')[1]).forEach(function (value, key) {
      result[key] = value;
    });

    let objToSend: NavigationExtras = {
      queryParams: result
    };

    this.router.navigate([this.turSiteName], objToSend).then(() => {
      window.location.reload();
    });
  }

  turChangeSort(event: Event) {
    console.log("DDDD: " + this.turSort);
    console.log("RRR");
    // this.updateParameters();
    console.log("VVV");
    let browserURL: string = this.changeQueryStringParameter(
      this.turQueryString, "sort",
      this.turSort);
    browserURL = this.changeQueryStringParameter(
      browserURL, "q",
      this.turQuery);
    browserURL = this.changeQueryStringParameter(
      browserURL, "p",
      this.turPage);
    //console.log(this.turSiteName);
    //console.log(this.turSiteName + "?" + browserURL);
    this.turRedirect(this.turSiteName + "?" + browserURL);

  }

  changeQueryStringParameter(uri: string, key: string, val: string): string {
    var regex = new RegExp('(?<=[?|&])(' + key + '=)[^\&]+', 'i');
    // return url.replace(regex, param + '=$1' + value);
    return uri.replace(regex, key + '=' + val);
  }

  camelize(str: string): string {
    return str.replace(/(?:^\w|[A-Z]|\b\w|\s+)/g, function (match, index) {
      if (+match === 0) return ""; // or if (/\s+/.test(match)) for white spaces
      return index === 0 ? match.toUpperCase() : match.toLowerCase();
    });
  }
  ngOnInit(): void {
  }

  title = 'sn';
}
