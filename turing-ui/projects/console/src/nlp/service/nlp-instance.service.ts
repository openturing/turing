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
import { Observable } from 'rxjs';
import { environment } from '../../../../../environments/environment';
import { TurNLPInstance } from '../model/nlp-instance.model';

@Injectable()
export class TurNLPInstanceService {

  constructor(private httpClient: HttpClient) { }

  query(): Observable<TurNLPInstance[]> {
    return this.httpClient.get<TurNLPInstance[]>(`${environment.apiUrl}/api/nlp`);
  }

  get(id: string): Observable<TurNLPInstance> {
    return this.httpClient.get<TurNLPInstance>(`${environment.apiUrl}/api/nlp/${id}`);
  }

  getStructure(): Observable<TurNLPInstance> {
    return this.httpClient.get<TurNLPInstance>(`${environment.apiUrl}/api/nlp/structure`);
  }

  public save(turNLPInstance: TurNLPInstance, newObject: boolean): Observable<TurNLPInstance> {
    if (newObject) {
      return this.httpClient.post<TurNLPInstance>(`${environment.apiUrl}/api/nlp`,
        JSON.stringify(turNLPInstance));
    }
    else {
      return this.httpClient.put<TurNLPInstance>(`${environment.apiUrl}/api/nlp/${turNLPInstance.id}`,
        JSON.stringify(turNLPInstance));
    }
  }

  public delete(turNLPInstance: TurNLPInstance): Observable<TurNLPInstance> {
    return this.httpClient.delete<TurNLPInstance>(`${environment.apiUrl}/api/nlp/${turNLPInstance.id}`);

  }

}
