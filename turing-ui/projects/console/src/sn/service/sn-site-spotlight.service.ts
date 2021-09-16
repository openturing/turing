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
import { TurSNSiteSpotlight } from '../model/sn-site-spotlight.model';

@Injectable()
export class TurSNSiteSpotlightService {
  constructor(private httpClient: HttpClient) { }

  query(siteId: string): Observable<TurSNSiteSpotlight[]> {
    return this.httpClient.get<TurSNSiteSpotlight[]>(`${environment.apiUrl}/api/sn/${siteId}/spotlight`);
  }

  get(siteId: string, id: string): Observable<TurSNSiteSpotlight> {
    return this.httpClient.get<TurSNSiteSpotlight>(`${environment.apiUrl}/api/sn/${siteId}/spotlight/${id}`);
  }

  public save(siteId: string, turSNSiteSpotlight: TurSNSiteSpotlight): Observable<TurSNSiteSpotlight> {
    return this.httpClient.put<TurSNSiteSpotlight>(`${environment.apiUrl}/api/sn/${siteId}/spotlight/${turSNSiteSpotlight.id}`,
      JSON.stringify(turSNSiteSpotlight));

  }

}
