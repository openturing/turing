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
import { TurIntegrationInstance } from '../model/integration-instance.model';

@Injectable()
export class TurIntegrationInstanceService {

  constructor(private httpClient: HttpClient) { }

  query(): Observable<TurIntegrationInstance[]> {
    return this.httpClient.get<TurIntegrationInstance[]>(`${environment.apiUrl}/api/integration`);
  }

  get(id: string): Observable<TurIntegrationInstance> {
    return this.httpClient.get<TurIntegrationInstance>(`${environment.apiUrl}/api/integration/${id}`);
  }

  getStructure(): Observable<TurIntegrationInstance> {
    return this.httpClient.get<TurIntegrationInstance>(`${environment.apiUrl}/api/integration/structure`);
  }

  public save(turIntegrationInstance: TurIntegrationInstance, newObject: boolean): Observable<TurIntegrationInstance> {
    if (newObject) {
      return this.httpClient.post<TurIntegrationInstance>(`${environment.apiUrl}/api/integration`,
        JSON.stringify(turIntegrationInstance));
    }
    else {
      return this.httpClient.put<TurIntegrationInstance>(`${environment.apiUrl}/api/integration/${turIntegrationInstance.id}`,
        JSON.stringify(turIntegrationInstance));
    }
  }
  public delete(turIntegrationInstance: TurIntegrationInstance): Observable<Object> {
    return this.httpClient.delete(`${environment.apiUrl}/api/integration/${turIntegrationInstance.id}`);
  }
}
