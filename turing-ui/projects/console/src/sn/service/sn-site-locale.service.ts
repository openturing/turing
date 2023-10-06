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
import { TurSNSiteLocale } from '../model/sn-site-locale.model';
import { TurSNSite } from '../model/sn-site.model';

@Injectable()
export class TurSNSiteLocaleService {
  constructor(private httpClient: HttpClient) { }

  query(siteId: string): Observable<TurSNSiteLocale[]> {
    return this.httpClient.get<TurSNSiteLocale[]>(`${environment.apiUrl}/api/sn/${siteId}/locale`);
  }

  get(siteId: string, id: string): Observable<TurSNSiteLocale> {
    return this.httpClient.get<TurSNSiteLocale>(`${environment.apiUrl}/api/sn/${siteId}/locale/${id}`);
  }

  getStructure(siteId: string): Observable<TurSNSiteLocale> {
    return this.httpClient.get<TurSNSiteLocale>(`${environment.apiUrl}/api/sn/${siteId}/locale/structure`);
  }

  public save(turSNSiteLocale: TurSNSiteLocale, newObject: boolean): Observable<TurSNSiteLocale> {
    if (newObject) {
      return this.httpClient.post<TurSNSiteLocale>(`${environment.apiUrl}/api/sn/${turSNSiteLocale.turSNSite.id}/locale`,
        JSON.stringify(turSNSiteLocale));
    }
    else {
      return this.httpClient.put<TurSNSiteLocale>(`${environment.apiUrl}/api/sn/${turSNSiteLocale.turSNSite.id}/locale/${turSNSiteLocale.id}`,
        JSON.stringify(turSNSiteLocale));
    }
  }
  public delete(turSNSiteLocale: TurSNSiteLocale): Observable<TurSNSiteLocale> {
    return this.httpClient.delete<TurSNSiteLocale>(`${environment.apiUrl}/api/sn/${turSNSiteLocale.turSNSite.id}/locale/${turSNSiteLocale.id}`);

  }
}
