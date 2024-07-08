import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { NotifierService } from 'angular-notifier-updated';
import {ActivatedRoute, Router} from '@angular/router';
import {TurIntegrationAemSource} from "../../../model/integration-aem-source.model";
import {TurIntegrationAemSourceService} from "../../../service/integration-aem-source.service";

@Component({
  selector: 'integration-aem-list-page',
  templateUrl: './integration-aem-list-page.component.html'
})
export class TurIntegrationAemListPageComponent implements OnInit {
  private turIntegrationAemSources: Observable<TurIntegrationAemSource[]>;
  private integrationId: string;
  filterText: string;
  constructor(private readonly notifier: NotifierService,
              private turIntegrationAemSourceService: TurIntegrationAemSourceService,
              private activatedRoute: ActivatedRoute,
              private router: Router) {

    this.integrationId = this.activatedRoute.parent?.parent?.snapshot.paramMap.get('id') || "";
    turIntegrationAemSourceService.setIntegrationId(this.integrationId);
    this.turIntegrationAemSources = turIntegrationAemSourceService.query();
    this.filterText = "";
  }
  getIntegrationId(): string {
    return this.integrationId;
  }
  getTurIntegrationAemSources(): Observable<TurIntegrationAemSource[]> {

    return this.turIntegrationAemSources;
  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
