import { Component, OnInit } from '@angular/core';
import { Observable } from 'rxjs';
import { TurStoreInstance } from '../../model/store-instance.model';
import { NotifierService } from 'angular-notifier-updated';
import { TurStoreInstanceService } from '../../service/store-instance.service';
import { Router, RouterModule } from '@angular/router';

@Component({
    selector: 'store-instance-list-page',
    templateUrl: './store-instance-list-page.component.html',
    standalone: false
})
export class TurStoreInstanceListPageComponent implements OnInit {
  private turStoreInstances: Observable<TurStoreInstance[]>;
  filterText: string;

  constructor(private readonly notifier: NotifierService,
              private turStoreInstanceService: TurStoreInstanceService,
              private router: Router) {
    this.turStoreInstances = turStoreInstanceService.query();
    this.filterText = "";
  }

  getTurStoreInstances(): Observable<TurStoreInstance[]> {

    return this.turStoreInstances;
  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
