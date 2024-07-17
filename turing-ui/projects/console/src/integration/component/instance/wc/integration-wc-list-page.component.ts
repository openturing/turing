import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { NotifierService } from 'angular-notifier-updated';
import {ActivatedRoute, Router} from '@angular/router';
import {TurIntegrationWcSource} from "../../../model/integration-wc-source.model";
import {TurIntegrationWcSourceService} from "../../../service/integration-wc-source.service";

@Component({
  selector: 'integration-wc-list-page',
  templateUrl: './integration-wc-list-page.component.html'
})
export class TurIntegrationWcListPageComponent implements OnInit {
  private turIntegrationWcSources: Observable<TurIntegrationWcSource[]>;
  private integrationId: string;
  filterText: string;
  constructor(private readonly notifier: NotifierService,
              private turIntegrationWcSourceService: TurIntegrationWcSourceService,
              private activatedRoute: ActivatedRoute,
              private router: Router) {

    this.integrationId = this.activatedRoute.parent?.snapshot.paramMap.get('id') || "";
    turIntegrationWcSourceService.setIntegrationId(this.integrationId);
    this.turIntegrationWcSources = turIntegrationWcSourceService.query();
    this.filterText = "";
  }
  getIntegrationId(): string {
    return this.integrationId;
  }
  getTurIntegrationWcSources(): Observable<TurIntegrationWcSource[]> {

    return this.turIntegrationWcSources;
  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
