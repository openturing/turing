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
import { TurSNSiteMetricsTerm } from '../model/sn-site-metrics-term.model';

@Injectable()
export class TurSNSiteMetricsService {
  constructor(private httpClient: HttpClient) { }

  topTermsAllTime(siteId: string, rows: number): Observable<TurSNSiteMetricsTerm[]> {
    return this.httpClient.get<TurSNSiteMetricsTerm[]>(`${environment.apiUrl}/api/sn/${siteId}/metrics/top-terms/all-time/${rows}`);
  }
  topTermsToday(siteId: string, rows: number): Observable<TurSNSiteMetricsTerm[]> {
    return this.httpClient.get<TurSNSiteMetricsTerm[]>(`${environment.apiUrl}/api/sn/${siteId}/metrics/top-terms/today/${rows}`);
  }
  topTermsThisWeek(siteId: string, rows: number): Observable<TurSNSiteMetricsTerm[]> {
    return this.httpClient.get<TurSNSiteMetricsTerm[]>(`${environment.apiUrl}/api/sn/${siteId}/metrics/top-terms/this-week/${rows}`);
  }
  topTermsThisMonth(siteId: string, rows: number): Observable<TurSNSiteMetricsTerm[]> {
    return this.httpClient.get<TurSNSiteMetricsTerm[]>(`${environment.apiUrl}/api/sn/${siteId}/metrics/top-terms/this-month/${rows}`);
  }
}
