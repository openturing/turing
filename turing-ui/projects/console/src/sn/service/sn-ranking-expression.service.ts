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
import {TurSNRankingExpression} from "../model/sn-ranking-expression.model";

@Injectable()
export class TurSNRankingExpressionService {
  constructor(private httpClient: HttpClient) { }

  query(siteId: string): Observable<TurSNRankingExpression[]> {
    return this.httpClient.get<TurSNRankingExpression[]>(`${environment.apiUrl}/api/sn/${siteId}/ranking-expression`);
  }

  get(siteId: string, id: string): Observable<TurSNRankingExpression> {
    return this.httpClient.get<TurSNRankingExpression>(`${environment.apiUrl}/api/sn/${siteId}/ranking-expression/${id}`);
  }

  getStructure(siteId: string): Observable<TurSNRankingExpression> {
    return this.httpClient.get<TurSNRankingExpression>(`${environment.apiUrl}/api/sn/${siteId}/ranking-expression/structure`);
  }

  public save(siteId: string, turSNRankingExpression: TurSNRankingExpression, newObject: boolean): Observable<TurSNRankingExpression> {
    if (newObject) {
      return this.httpClient.post<TurSNRankingExpression>(`${environment.apiUrl}/api/sn/${siteId}/ranking-expression`,
        JSON.stringify(turSNRankingExpression));
    }
    else {
      return this.httpClient.put<TurSNRankingExpression>(`${environment.apiUrl}/api/sn/${siteId}/ranking-expression/${turSNRankingExpression.id}`,
        JSON.stringify(turSNRankingExpression));
    }
  }

  public delete(siteId: string, turSNRankingExpression: TurSNRankingExpression): Observable<TurSNRankingExpression> {
    return this.httpClient.delete<TurSNRankingExpression>(`${environment.apiUrl}/api/sn/${siteId}/ranking-expression/${turSNRankingExpression.id}`);
  }
}
