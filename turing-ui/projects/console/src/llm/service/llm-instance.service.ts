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
import { TurLLMInstance } from '../model/llm-instance.model';

@Injectable()
export class TurLLMInstanceService {

  constructor(private httpClient: HttpClient) { }

  query(): Observable<TurLLMInstance[]> {
    return this.httpClient.get<TurLLMInstance[]>(`${environment.apiUrl}/api/llm`);
  }

  get(id: string): Observable<TurLLMInstance> {
    return this.httpClient.get<TurLLMInstance>(`${environment.apiUrl}/api/llm/${id}`);
  }

  getStructure(): Observable<TurLLMInstance> {
    return this.httpClient.get<TurLLMInstance>(`${environment.apiUrl}/api/llm/structure`);
  }

  public save(turLLMInstance: TurLLMInstance, newObject: boolean): Observable<TurLLMInstance> {
    if (newObject) {
      return this.httpClient.post<TurLLMInstance>(`${environment.apiUrl}/api/llm`,
        JSON.stringify(turLLMInstance));
    }
    else {
      return this.httpClient.put<TurLLMInstance>(`${environment.apiUrl}/api/llm/${turLLMInstance.id}`,
        JSON.stringify(turLLMInstance));
    }
  }
  public delete(turLLMInstance: TurLLMInstance): Observable<Object> {
    return this.httpClient.delete(`${environment.apiUrl}/api/llm/${turLLMInstance.id}`);
  }
}
