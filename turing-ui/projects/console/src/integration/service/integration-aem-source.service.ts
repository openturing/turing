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
import {TurIntegrationAemSource} from "../model/integration-aem-source.model";

@Injectable()
export class TurIntegrationAemSourceService {

  constructor(private httpClient: HttpClient) { }

  query(): Observable<TurIntegrationAemSource[]> {
    return this.httpClient.get<TurIntegrationAemSource[]>(`http://localhost:30110/api/v2/aem/source`);
  }

  get(id: string): Observable<TurIntegrationAemSource> {
    return this.httpClient.get<TurIntegrationAemSource>(`http://localhost:30110/api/v2/aem/source/${id}`);
  }

  getStructure(): Observable<TurIntegrationAemSource> {
    return this.httpClient.get<TurIntegrationAemSource>(`http://localhost:30110/api/v2/aem/source/structure`);
  }

  public save(turIntegrationInstance: TurIntegrationAemSource, newObject: boolean): Observable<TurIntegrationAemSource> {
    if (newObject) {
      return this.httpClient.post<TurIntegrationAemSource>(`http://localhost:30110/api/v2/aem/source`,
        JSON.stringify(turIntegrationInstance));
    }
    else {
      return this.httpClient.put<TurIntegrationAemSource>(`http://localhost:30110/api/v2/aem/source/${turIntegrationInstance.id}`,
        JSON.stringify(turIntegrationInstance));
    }
  }
  public delete(turIntegrationAemSource: TurIntegrationAemSource): Observable<Object> {
    return this.httpClient.delete(`http://localhost:30110/api/v2/aem/source/${turIntegrationAemSource.id}`);
  }
}
