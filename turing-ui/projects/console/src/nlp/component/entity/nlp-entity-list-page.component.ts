import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurNLPEntity } from '../../model/nlp-entity.model';
import { NotifierService } from 'angular-notifier-updated';
import { TurNLPEntityService } from '../../service/nlp-entity.service';
import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'nlp-entity-page',
  templateUrl: './nlp-entity-list-page.component.html'
})
export class TurNLPEntityListPageComponent implements OnInit {
  private turNLPEntities: Observable<TurNLPEntity[]>;
  filterText: string;

  constructor(private readonly notifier: NotifierService,
              private turNLPEntityService: TurNLPEntityService,
              private router: Router) {
    this.turNLPEntities = turNLPEntityService.query();
    this.filterText = "";
  }

  getTurNLPEntities(): Observable<TurNLPEntity[]> {

    return this.turNLPEntities;
  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
