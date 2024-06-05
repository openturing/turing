import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurIntegrationInstance } from '../../model/integration-instance.model';
import { NotifierService } from 'angular-notifier-updated';
import { TurIntegrationInstanceService } from '../../service/integration-instance.service';
import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'integration-instance-list-page',
  templateUrl: './integration-instance-list-page.component.html'
})
export class TurIntegrationInstanceListPageComponent implements OnInit {
  private turIntegrationInstances: Observable<TurIntegrationInstance[]>;
  filterText: string;

  constructor(private readonly notifier: NotifierService,
              private turIntegrationInstanceService: TurIntegrationInstanceService,
              private router: Router) {
    this.turIntegrationInstances = turIntegrationInstanceService.query();
    this.filterText = "";
  }

  getTurIntegrationInstances(): Observable<TurIntegrationInstance[]> {

    return this.turIntegrationInstances;
  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
