import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { NotifierService } from 'angular-notifier-updated';
import { Router } from '@angular/router';
import {TurDevTokenService} from "../../service/dev-token.service";
import {TurDevToken} from "../../model/dev-token.model";

@Component({
    selector: 'dev-token-list-page',
    templateUrl: './dev-token-list-page.component.html',
    standalone: false
})
export class TurDevTokenListPageComponent implements OnInit {
  private turDevTokens: Observable<TurDevToken[]>;
  filterText: string;

  constructor(private readonly notifier: NotifierService,
              private turDevTokenService: TurDevTokenService,
              private router: Router) {
    this.turDevTokens = turDevTokenService.query();
    this.filterText = "";
  }

  getTurDevTokens(): Observable<TurDevToken[]> {

    return this.turDevTokens;
  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
