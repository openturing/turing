import {Injectable} from '@angular/core';
import {Router, ActivatedRouteSnapshot, RouterStateSnapshot} from '@angular/router';
import {AuthenticationService} from "../../../../welcome/src/app/_services";
import { HttpClient, HttpHeaders, HttpResponse } from "@angular/common/http";
import {environment} from '../../../../../environments/environment';
import {TurDiscoveryAPI} from "../_model/discovery.model";

@Injectable({providedIn: 'root'})
export class AuthGuard {
  constructor(
    private router: Router,
    private authenticationService: AuthenticationService,
    private http: HttpClient
  ) {
  }

  canActivate(route: ActivatedRouteSnapshot, state: RouterStateSnapshot) {
    return this.http.get<TurDiscoveryAPI>(`${environment.apiUrl}/api/discovery`)
      .subscribe(discovery => {
        if (discovery.keycloak) {
          return true;
        } else {
          const user = this.authenticationService.userValue;
          if (user && Object.keys(user).length != 0) {
            // logged in so return true
            return true;
          }
          window.location.href = "/welcome?returnUrl=/console" + state.url;
          return false;
        }
      })
  }
}
