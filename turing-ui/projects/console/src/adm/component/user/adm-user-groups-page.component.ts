import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { TurAdmUser } from '../../model/adm-user.model';
import { NotifierService } from 'angular-notifier';
import { TurAdmUserService } from '../../service/adm-user.service';
import { ActivatedRoute, Router } from '@angular/router';
import { UntypedFormControl, Validators } from '@angular/forms';

@Component({
  selector: 'adm-user-groups-page',
  templateUrl: './adm-user-groups-page.component.html'
})
export class TurAdmUserGroupsPageComponent implements OnInit {
  @ViewChild('modalDelete')
  modalDelete!: ElementRef;
  private turAdmUser: Observable<TurAdmUser>;
  private newObject: boolean = false;

  portControl = new UntypedFormControl(80, [Validators.max(100), Validators.min(0)])


  constructor(
    private readonly notifier: NotifierService,
    private turAdmUserService: TurAdmUserService,
    private activatedRoute: ActivatedRoute,
    private router: Router) {

    let username: string = this.activatedRoute.parent?.snapshot.paramMap.get('username') || "";

    this.newObject = ( username != null && username.toLowerCase() === 'new');

    this.turAdmUser = this.newObject ? this.turAdmUserService.getStructure() : this.turAdmUserService.get(username);

  }

  isNewObject(): boolean {
    return this.newObject;
  }

  saveButtonCaption(): string {
    return this.newObject ? "Create user" : "Update user";
  }

  getTurAdmUser(): Observable<TurAdmUser> {
    return this.turAdmUser;
  }

  ngOnInit(): void {
  }

  public save(_turAdmUser: TurAdmUser) {
    this.turAdmUserService.save(_turAdmUser, this.newObject).subscribe(
      (turAdmUser: TurAdmUser) => {
        let message: string = this.newObject ? " user was created." : " user was updated.";

        _turAdmUser = turAdmUser;

        this.notifier.notify("success", turAdmUser.username.concat(message));

        this.router.navigate(['/adm/user']);
      },
      response => {
        this.notifier.notify("error", "user was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });
  }

  public delete(_turAdmUser: TurAdmUser) {
    this.turAdmUserService.delete(_turAdmUser).subscribe(
      (turAdmUser: TurAdmUser) => {
        _turAdmUser = turAdmUser;
        this.notifier.notify("success", turAdmUser.username.concat(" user was deleted."));
        this.modalDelete.nativeElement.removeAttribute("open");
      },
      response => {
        this.notifier.notify("error", "user was error: " + response);
      },
      () => {
        // console.log('The POST observable is now completed.');
      });
  }
}
