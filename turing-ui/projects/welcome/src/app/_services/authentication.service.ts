import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { map } from 'rxjs/operators';

import { User, TurRestInfo } from '../_models';
import {environment} from "../../../../../environments/environment";

@Injectable({ providedIn: 'root' })
export class AuthenticationService {
  private userSubject: BehaviorSubject<User>;
  public user: Observable<User>;

  constructor(
    private router: Router,
    private http: HttpClient
  ) {
    this.userSubject = new BehaviorSubject<User>(JSON.parse(localStorage.getItem('restInfo') || "{}"));
    this.user = this.userSubject.asObservable();
  }

  public get userValue(): User {
    return this.userSubject.value;
  }

  login(username: string, password: string) {
    const headers = new HttpHeaders({
      authorization: 'Basic ' +  window.btoa(username + ':' + password)
    });
    let userRest = new User();
    return this.http.get<TurRestInfo>(`${environment.apiUrl}/api/v2`, { headers: headers })
      .pipe(map(turRestInfo => {
        // store user details and basic auth credentials in local storage to keep user logged in between page refreshes
        turRestInfo.authdata =  window.btoa(username + ':' + password);
        localStorage.setItem('restInfo', JSON.stringify(turRestInfo));

        userRest.authdata = turRestInfo.authdata;
        this.userSubject.next(userRest);
        return userRest;
      }));
  }

  logout() {
    // remove user from local storage to log user out
    localStorage.removeItem('restInfo');
    this.userSubject.next(new User());
   // window.location.href = '/welcome';
  }
}
