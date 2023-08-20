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
import { environment } from '../../environments/environment';
import { TurSNSiteMerge } from '../model/sn-site-merge.model';

@Injectable()
export class TurSNSiteMergeService {
  constructor(private httpClient: HttpClient) { }

  query(siteId: string): Observable<TurSNSiteMerge[]> {
    return this.httpClient.get<TurSNSiteMerge[]>(`${environment.apiUrl}/api/sn/${siteId}/merge`);
  }

  get(siteId: string, id: string): Observable<TurSNSiteMerge> {
    return this.httpClient.get<TurSNSiteMerge>(`${environment.apiUrl}/api/sn/${siteId}/merge/${id}`);
  }

  getStructure(siteId: string): Observable<TurSNSiteMerge> {
    return this.httpClient.get<TurSNSiteMerge>(`${environment.apiUrl}/api/sn/${siteId}/merge/structure`);
  }

  public save(turSNSiteMerge: TurSNSiteMerge, newObject: boolean): Observable<TurSNSiteMerge> {
    if (newObject) {
      return this.httpClient.post<TurSNSiteMerge>(`${environment.apiUrl}/api/sn/${turSNSiteMerge.turSNSite.id}/merge`,
        JSON.stringify(turSNSiteMerge));
    }
    else {
      return this.httpClient.put<TurSNSiteMerge>(`${environment.apiUrl}/api/sn/${turSNSiteMerge.turSNSite.id}/merge/${turSNSiteMerge.id}`,
        JSON.stringify(turSNSiteMerge));
    }
  }
  public delete(turSNSiteMerge: TurSNSiteMerge): Observable<TurSNSiteMerge> {
    return this.httpClient.delete<TurSNSiteMerge>(`${environment.apiUrl}/api/sn/${turSNSiteMerge.turSNSite.id}/merge/${turSNSiteMerge.id}`);

  }
}
