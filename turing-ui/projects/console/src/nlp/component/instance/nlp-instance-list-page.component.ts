import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurNLPInstance } from '../../model/nlp-instance.model';
import { NotifierService } from 'angular-notifier';
import { TurNLPInstanceService } from '../../service/nlp-instance.service';
import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'nlp-instance-list-page',
  templateUrl: './nlp-instance-list-page.component.html'
})
export class TurNLPInstanceListPageComponent implements OnInit {
  private turNLPInstances: Observable<TurNLPInstance[]>;
  filterText: string;

  constructor(private readonly notifier: NotifierService,
              private turNLPInstanceService: TurNLPInstanceService,
              private router: Router) {
    this.turNLPInstances = turNLPInstanceService.query();
    this.filterText = "";
  }

  getTurNLPInstances(): Observable<TurNLPInstance[]> {

    return this.turNLPInstances;
  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
