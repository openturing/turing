import { Component, OnInit } from '@angular/core';
import { NotifierService } from 'angular-notifier';
import { Router } from '@angular/router';
import { TurAdmUser } from '../../model/adm-user.model';
import { Observable } from 'rxjs';
import { TurAdmUserService } from '../../service/adm-user.service';

@Component({
  selector: 'adm-user-list-page',
  templateUrl: './adm-user-list-page.component.html'
})
export class TurAdmUserListPageComponent implements OnInit {
  private turAdmUsers: Observable<TurAdmUser[]>;

  constructor(
    private readonly notifier: NotifierService,
    private router: Router,
    private turAdmUserService: TurAdmUserService) {
    this.turAdmUsers = turAdmUserService.query();
  }

  getTurAdmUsers(): Observable<TurAdmUser[]> {

    return this.turAdmUsers;
  }

  getRouter(): Router {
    return this.router;
  }

  ngOnInit(): void {
  }
}
