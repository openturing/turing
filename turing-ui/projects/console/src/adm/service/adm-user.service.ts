/*
 * Copyright (C) 2016-2021 the original author or authors.
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
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { TurAdmUser } from '../model/adm-user.model';

@Injectable()
export class TurAdmUserService {
  private readonly PREFIX_URL = "/api/v2/user";

  constructor(private httpClient: HttpClient) { }

  query(): Observable<TurAdmUser[]> {
    return this.httpClient.get<TurAdmUser[]>(`${environment.apiUrl}${this.PREFIX_URL}`);
  }
  get(username: string): Observable<TurAdmUser> {
    return this.httpClient.get<TurAdmUser>(`${environment.apiUrl}${this.PREFIX_URL}/${username}`);
  }

  getStructure(): Observable<TurAdmUser> {
    return this.httpClient.get<TurAdmUser>(`${environment.apiUrl}${this.PREFIX_URL}/structure`);
  }

  public save(turAdmUser: TurAdmUser, newObject: boolean): Observable<TurAdmUser> {
    if (newObject) {
      return this.httpClient.post<TurAdmUser>(`${environment.apiUrl}${this.PREFIX_URL}r`,
        JSON.stringify(turAdmUser));
    }
    else {
      return this.httpClient.put<TurAdmUser>(`${environment.apiUrl}${this.PREFIX_URL}/${turAdmUser.username}`,
        JSON.stringify(turAdmUser));
    }
  }
  public delete(turAdmUser: TurAdmUser): Observable<TurAdmUser> {
    return this.httpClient.delete<TurAdmUser>(`${environment.apiUrl}${this.PREFIX_URL}/${turAdmUser.username}`);
  }
}
