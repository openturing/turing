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
import { environment } from '../../../../../environments/environment';
import { TurStoreInstance } from '../model/store-instance.model';

@Injectable()
export class TurStoreInstanceService {

  constructor(private httpClient: HttpClient) { }

  query(): Observable<TurStoreInstance[]> {
    return this.httpClient.get<TurStoreInstance[]>(`${environment.apiUrl}/api/store`);
  }

  get(id: string): Observable<TurStoreInstance> {
    return this.httpClient.get<TurStoreInstance>(`${environment.apiUrl}/api/store/${id}`);
  }

  getStructure(): Observable<TurStoreInstance> {
    return this.httpClient.get<TurStoreInstance>(`${environment.apiUrl}/api/store/structure`);
  }

  public save(turStoreInstance: TurStoreInstance, newObject: boolean): Observable<TurStoreInstance> {
    if (newObject) {
      return this.httpClient.post<TurStoreInstance>(`${environment.apiUrl}/api/store`,
        JSON.stringify(turStoreInstance));
    }
    else {
      return this.httpClient.put<TurStoreInstance>(`${environment.apiUrl}/api/store/${turStoreInstance.id}`,
        JSON.stringify(turStoreInstance));
    }
  }
  public delete(turStoreInstance: TurStoreInstance): Observable<Object> {
    return this.httpClient.delete(`${environment.apiUrl}/api/store/${turStoreInstance.id}`);
  }
}
