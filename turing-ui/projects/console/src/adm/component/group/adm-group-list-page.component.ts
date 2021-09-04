import { Component, OnInit } from '@angular/core';
import { NotifierService } from 'angular-notifier';
import { Router } from '@angular/router';
import { TurAdmGroup } from '../../model/adm-group.model';
import { Observable } from 'rxjs';
import { TurAdmGroupService } from '../../service/adm-group.service';

@Component({
  selector: 'adm-group-list-page',
  templateUrl: './adm-group-list-page.component.html'
})
export class TurAdmGroupListPageComponent implements OnInit {
  private turAdmGroups: Observable<TurAdmGroup[]>;

  constructor(
    private readonly notifier: NotifierService,
    private router: Router,
    private turAdmGroupService: TurAdmGroupService) {
    this.turAdmGroups = turAdmGroupService.query();
  }

  getTurAdmGroups(): Observable<TurAdmGroup[]> {

    return this.turAdmGroups;
  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
