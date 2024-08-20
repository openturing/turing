import { Component, OnInit } from '@angular/core';
import { NotifierService } from 'angular-notifier-updated';
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
  public delete(_turAdmUser: TurAdmUser) {
    if (_turAdmUser.username.toLowerCase() == "admin") {
      this.notifier.notify("error", "Can not delete Admin user, because it is essential");
    }
    else {
      this.turAdmUserService.delete(_turAdmUser).subscribe(
        (turAdmUser: TurAdmUser) => {
          this.notifier.notify("success", _turAdmUser.username.concat(" user was deleted."));
          this.router.navigate(['/adm']);
        },
        response => {
          this.notifier.notify("error", "user was error: " + response);
        },
        () => {
          // console.log('The POST observable is now completed.');
        });
    }
  }
}
