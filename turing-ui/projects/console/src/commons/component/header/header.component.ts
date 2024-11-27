import {Component, OnInit} from '@angular/core';
import { Observable } from 'rxjs';
import {User} from "../../../../../welcome/src/app/_models";
import {AuthenticationService, UserService} from "../../../../../welcome/src/app/_services";

@Component({
    selector: 'tur-header',
    templateUrl: './header.component.html',
    standalone: false
})
export class TurHeaderComponent implements OnInit {
  user!: Observable<User>;

  constructor(private userService: UserService, private authenticationService: AuthenticationService) { }


  logout($event: MouseEvent) {
    this.authenticationService.logout();
  }

  ngOnInit(): void {
    this.user = this.userService.getAll();
  }

}
