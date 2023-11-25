import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurSEInstance } from '../../model/se-instance.model';
import { NotifierService } from 'angular-notifier';
import { TurSEInstanceService } from '../../service/se-instance.service';
import { Router, RouterModule } from '@angular/router';

@Component({
  selector: 'se-instance-list-page',
  templateUrl: './se-instance-list-page.component.html'
})
export class TurSEInstanceListPageComponent implements OnInit {
  private turSEInstances: Observable<TurSEInstance[]>;
  filterText: string;

  constructor(private readonly notifier: NotifierService,
              private turSEInstanceService: TurSEInstanceService,
              private router: Router) {
    this.turSEInstances = turSEInstanceService.query();
    this.filterText = "";
  }

  getTurSEInstances(): Observable<TurSEInstance[]> {

    return this.turSEInstances;
  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
