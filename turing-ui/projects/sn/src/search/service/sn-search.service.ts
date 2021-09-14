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
import { environment } from '../../environments/environment';
import { TurSNSearch } from '../model/sn-search.model';

@Injectable()
export class TurSNSearchService {

  constructor(private httpClient: HttpClient) { }
  query(turSiteName: string, q: string, p: string, _setlocale: string, sort: string, fq: string[], tr: string[]): Observable<TurSNSearch> {

    let queryString: string = TurSNSearchService.generateQueryString(q, p, _setlocale, sort, fq, tr);
    return this.httpClient.get<TurSNSearch>(`${environment.apiUrl}/api/sn/${turSiteName}/search?${queryString}`);
  }

  public static generateQueryString(q: string, p: string, _setlocale: string, sort: string, fq: string[], tr: string[]) {
    let queryString = "";
    if (q) {
      queryString += `q=${q}`;
    } else {
      queryString += `q=*`;
    }

    if (p) {
      queryString += `&p=${p}`;
    }
    else {
      queryString += `&p=1`;
    }

    if (_setlocale) {
      queryString += `&_setlocale=${_setlocale}`;
    }

    if (sort) {
      queryString += `&sort=${sort}`;
    }
    else {
      queryString += `&sort=relevance`;
    }

    if (fq) {
      if (Array.isArray(fq)) {
        fq.forEach(function (fqItem) {
          queryString += `&fq[]=${fqItem}`;
        });
      } else {
        queryString += `&fq[]=${fq}`;
      }

    }

    if (tr) {
      if (Array.isArray(tr)) {
        fq.forEach(function (trItem) {
          queryString += `&tr[]=${trItem}`;
        });
      } else {
        queryString += `&tr[]=${tr}`;
      }

    }

    return queryString;
  }
}
