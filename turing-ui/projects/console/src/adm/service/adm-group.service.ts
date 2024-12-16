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
import { TurAdmGroup } from '../model/adm-group.model';

@Injectable()
export class TurAdmGroupService {
  private readonly PREFIX_URL = "/api/v2/group";

  constructor(private httpClient: HttpClient) { }

  query(): Observable<TurAdmGroup[]> {
    return this.httpClient.get<TurAdmGroup[]>(`${environment.apiUrl}${this.PREFIX_URL}`);
  }
  get(id: string): Observable<TurAdmGroup> {
    return this.httpClient.get<TurAdmGroup>(`${environment.apiUrl}${this.PREFIX_URL}/${id}`);
  }

  getStructure(): Observable<TurAdmGroup> {
    return this.httpClient.get<TurAdmGroup>(`${environment.apiUrl}${this.PREFIX_URL}/structure`);
  }

  public save(turAdmGroup: TurAdmGroup, newObject: boolean): Observable<TurAdmGroup> {
    if (newObject) {
      return this.httpClient.post<TurAdmGroup>(`${environment.apiUrl}${this.PREFIX_URL}r`,
        JSON.stringify(turAdmGroup));
    }
    else {
      return this.httpClient.put<TurAdmGroup>(`${environment.apiUrl}${this.PREFIX_URL}/${turAdmGroup.id}`,
        JSON.stringify(turAdmGroup));
    }
  }
  public delete(turAdmGroup: TurAdmGroup): Observable<TurAdmGroup> {
    return this.httpClient.delete<TurAdmGroup>(`${environment.apiUrl}${this.PREFIX_URL}/${turAdmGroup.id}`);
  }
}
