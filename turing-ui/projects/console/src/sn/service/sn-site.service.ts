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
import { TurSNSite } from '../model/sn-site.model';
import { TurSNSiteField } from '../model/sn-site-field.model';
import { map } from 'rxjs/operators';
import { TurSNSiteStatus } from '../model/sn-site-monitoring.model';

@Injectable()
export class TurSNSiteService {
  constructor(private httpClient: HttpClient) { }

  query(): Observable<TurSNSite[]> {
    return this.httpClient.get<TurSNSite[]>(`${environment.apiUrl}/api/sn`);
  }

  get(id: string): Observable<TurSNSite> {
    return this.httpClient.get<TurSNSite>(`${environment.apiUrl}/api/sn/${id}`);
  }

  getStructure(): Observable<TurSNSite> {
    return this.httpClient.get<TurSNSite>(`${environment.apiUrl}/api/sn/structure`);
  }

  getFields(id: string): Observable<TurSNSiteField[]> {
    return this.httpClient.get<TurSNSiteField[]>(`${environment.apiUrl}/api/sn/${id}/field/ext`);
  }

  getField(id: string, fieldId: string): Observable<TurSNSiteField> {
    return this.httpClient.get<TurSNSiteField>(`${environment.apiUrl}/api/sn/${id}/field/ext/${fieldId}`);
  }

  getFieldStructure(id: string): Observable<TurSNSiteField> {
    return this.httpClient.get<TurSNSiteField>(`${environment.apiUrl}/api/sn/${id}/field/ext/structure`);
  }

  getStatus(id: string): Observable<TurSNSiteStatus> {
    return this.httpClient.get<TurSNSiteStatus>(`${environment.apiUrl}/api/sn/${id}/monitoring`);
  }
  getFieldsByType(id: string, type: string): Observable<TurSNSiteField[]> {
    return this.httpClient.get<TurSNSiteField[]>(`${environment.apiUrl}/api/sn/${id}/field/ext`).pipe(map(items =>
      items.filter(item => item.snType.toLowerCase() === type)));
  }

  public save(turSNSite: TurSNSite, newObject: boolean): Observable<TurSNSite> {
    if (newObject) {
      return this.httpClient.post<TurSNSite>(`${environment.apiUrl}/api/sn`,
        JSON.stringify(turSNSite));
    }
    else {
      return this.httpClient.put<TurSNSite>(`${environment.apiUrl}/api/sn/${turSNSite.id}`,
        JSON.stringify(turSNSite));
    }
  }

  public delete(turSNSite: TurSNSite): Observable<TurSNSite> {
    return this.httpClient.delete<TurSNSite>(`${environment.apiUrl}/api/sn/${turSNSite.id}`);

  }

  public saveField(siteId: string, turSNSiteField: TurSNSiteField, newObject: boolean): Observable<TurSNSiteField> {
    if (newObject) {
      return this.httpClient.post<TurSNSiteField>(`${environment.apiUrl}/api/sn/${siteId}/field/ext`,
        JSON.stringify(turSNSiteField));
    }
    else {
      return this.httpClient.put<TurSNSiteField>(`${environment.apiUrl}/api/sn/${siteId}/field/ext/${turSNSiteField.id}`,
        JSON.stringify(turSNSiteField));
    }
  }

  public deleteField(siteId: string, turSNSiteField: TurSNSiteField): Observable<TurSNSiteField> {
    return this.httpClient.delete<TurSNSiteField>(`${environment.apiUrl}/api/sn/${siteId}/field/ext/${turSNSiteField.id}`);

  }
}
