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
import { TurSEInstance } from '../model/se-instance.model';

@Injectable()
export class TurSEInstanceService {

  constructor(private httpClient: HttpClient) { }

  query(): Observable<TurSEInstance[]> {
    return this.httpClient.get<TurSEInstance[]>(`${environment.apiUrl}/api/se`);
  }

  get(id: string): Observable<TurSEInstance> {
    return this.httpClient.get<TurSEInstance>(`${environment.apiUrl}/api/se/${id}`);
  }

  getStructure(): Observable<TurSEInstance> {
    return this.httpClient.get<TurSEInstance>(`${environment.apiUrl}/api/se/structure`);
  }

  public save(turSEInstance: TurSEInstance, newObject: boolean): Observable<TurSEInstance> {
    if (newObject) {
      return this.httpClient.post<TurSEInstance>(`${environment.apiUrl}/api/se`,
        JSON.stringify(turSEInstance));
    }
    else {
      return this.httpClient.put<TurSEInstance>(`${environment.apiUrl}/api/se/${turSEInstance.id}`,
        JSON.stringify(turSEInstance));
    }
  }

  public delete(turSEInstance: TurSEInstance): Observable<Object> {
    return this.httpClient.delete(`${environment.apiUrl}/api/se/${turSEInstance.id}`);

  }
}
