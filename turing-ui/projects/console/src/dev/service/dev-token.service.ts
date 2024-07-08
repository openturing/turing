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

import {Injectable} from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../../../environments/environment';
import {TurDevToken} from "../model/dev-token.model";
import {TurSEInstance} from "../../se/model/se-instance.model";
@Injectable()
export class TurDevTokenService {
  constructor(private httpClient: HttpClient) {
  }

  query(): Observable<TurDevToken[]> {
    return this.httpClient.get<TurDevToken[]>(`${environment.apiUrl}/api/dev/token`);
  }

  get(id: string): Observable<TurDevToken> {
    return this.httpClient.get<TurDevToken>(`${environment.apiUrl}/api/dev/token/${id}`);
  }

  getStructure(): Observable<TurDevToken> {
    return this.httpClient.get<TurDevToken>(`${environment.apiUrl}/api/dev/token/structure`);
  }
  public save(turDevToken: TurDevToken, newObject: boolean): Observable<TurDevToken> {
    if (newObject) {
      return this.httpClient.post<TurDevToken>(`${environment.apiUrl}/api/dev/token`,
        JSON.stringify(turDevToken));
    }
    else {
      return this.httpClient.put<TurDevToken>(`${environment.apiUrl}/api/dev/token/${turDevToken.id}`,
        JSON.stringify(turDevToken));
    }
  }
  public delete(turDevToken: TurDevToken): Observable<Object> {
    return this.httpClient.delete(`${environment.apiUrl}/api/dev/token/${turDevToken.id}`);
  }
}
