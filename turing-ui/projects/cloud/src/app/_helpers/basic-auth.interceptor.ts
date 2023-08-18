import { Injectable } from '@angular/core';
import { HttpRequest, HttpHandler, HttpEvent, HttpInterceptor, HttpXsrfTokenExtractor } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from './../../environments/environment';
import { AuthenticationService } from './../_services';

@Injectable()
export class BasicAuthInterceptor implements HttpInterceptor {
  constructor(private authenticationService: AuthenticationService, private xsrfTokenExtractor: HttpXsrfTokenExtractor) { }

  intercept(request: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const user = this.authenticationService.userValue;
    const isLoggedIn = user && Object.keys(user).length != 0 && user.authdata;
    const isApiUrl = request.url.startsWith(environment.apiUrl);
    let token = this.xsrfTokenExtractor.getToken() as string;
    if (token == null) {
      token = "";
    }
    if (isLoggedIn && isApiUrl) {
      request = request.clone({
        setHeaders: {
          Authorization: `Basic ${user.authdata}`,
          'X-Requested-With': 'XMLHttpRequest',
          'Content-Type': 'application/json',
          'X-XSRF-TOKEN': token
        }
      });

    }

    return next.handle(request);
  }
}
