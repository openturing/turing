/*
 * Copyright (C) 2016-2024 the original author or authors.
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
import {TurIntegrationWcSource} from "../model/integration-wc-source.model";
import { environment } from '../../../../../environments/environment';

@Injectable()
export class TurIntegrationWcSourceService {
  private integrationId: string | undefined;
  constructor(private httpClient: HttpClient) {}

  setIntegrationId(integrationId: string) {
    this.integrationId = integrationId;
  }
  query(): Observable<TurIntegrationWcSource[]> {
    return this.httpClient.get<TurIntegrationWcSource[]>(this.getUrl());
  }

  private getUrl() {
    return `${environment.apiUrl}/api/v2/integration/${this.integrationId}/wc/source`;
  }

  get(id: string): Observable<TurIntegrationWcSource> {
    return this.httpClient.get<TurIntegrationWcSource>(`${this.getUrl()}/${id}`);
  }

  getStructure(): Observable<TurIntegrationWcSource> {
    return this.httpClient.get<TurIntegrationWcSource>(`${this.getUrl()}/structure`);
  }

  public save(turIntegrationInstance: TurIntegrationWcSource, newObject: boolean): Observable<TurIntegrationWcSource> {
    if (newObject) {
      return this.httpClient.post<TurIntegrationWcSource>(this.getUrl(),
        JSON.stringify(turIntegrationInstance));
    }
    else {
      return this.httpClient.put<TurIntegrationWcSource>(`${this.getUrl()}/${turIntegrationInstance.id}`,
        JSON.stringify(turIntegrationInstance));
    }
  }
  public delete(turIntegrationWcSource: TurIntegrationWcSource): Observable<Object> {
    return this.httpClient.delete(`${this.getUrl()}/${turIntegrationWcSource.id}`);
  }
}
