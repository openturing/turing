/*
 * Copyright (C) 2016-2020 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs/operators';
import { environment } from '../../environments/environment';
import { User } from '../_models/user';
import { BehaviorSubject } from 'rxjs/internal/BehaviorSubject';

@Injectable()
export class TurSignupService {
  private userSubject: BehaviorSubject<User>;
  constructor(private httpClient: HttpClient) {
    this.userSubject = new BehaviorSubject<User>(JSON.parse("{}"));
  }

  signup(email: string, username: string, password: string) {

    const formData = new FormData();
    formData.append('email', email)
    formData.append('username', username);
    formData.append('password', password);

    return this.httpClient.post<any>(`${environment.apiUrl}/api/v2/guest/signup`, formData)
      .pipe(map(user => {
        this.userSubject.next(user);
        return user;
      }));
  }
}
