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
import { TurConverseAgent } from '../model/converse-agent.model';
import { TurConverseAgentEntity } from '../model/converse-agent-entity.model';

@Injectable()
export class TurConverseAgentService {
  private apiContext: string = `${environment.apiUrl}/api/converse/agent`;
  constructor(private httpClient: HttpClient) { }

  query(): Observable<TurConverseAgent[]> {
    return this.httpClient.get<TurConverseAgent[]>(`${this.apiContext}`);
  }

  get(id: string): Observable<TurConverseAgent> {
    return this.httpClient.get<TurConverseAgent>(`${this.apiContext}/${id}`);
  }

  getStructure(): Observable<TurConverseAgent> {
    return this.httpClient.get<TurConverseAgent>(`${this.apiContext}/structure`);
  }

  getEntities(id: string): Observable<TurConverseAgentEntity[]> {
    return this.httpClient.get<TurConverseAgentEntity[]>(`${this.apiContext}/${id}/entity`);
  }

  getEntity(id: string, entityId: string): Observable<TurConverseAgentEntity> {
    return this.httpClient.get<TurConverseAgentEntity>(`${this.apiContext}/${id}/entity/${entityId}`);
  }

   public save(turConverseAgent: TurConverseAgent, newObject: boolean): Observable<TurConverseAgent> {
    if (newObject) {
      return this.httpClient.post<TurConverseAgent>(`${this.apiContext}`,
        JSON.stringify(turConverseAgent));
    }
    else {
      return this.httpClient.put<TurConverseAgent>(`${this.apiContext}/${turConverseAgent.id}`,
        JSON.stringify(turConverseAgent));
    }
  }

  public delete(turConverseAgent: TurConverseAgent): Observable<TurConverseAgent> {
    return this.httpClient.delete<TurConverseAgent>(`${this.apiContext}/${turConverseAgent.id}`);
  }

  public saveEntity(id: string, turConverseAgentEntity: TurConverseAgentEntity): Observable<TurConverseAgentEntity> {
    return this.httpClient.put<TurConverseAgentEntity>(`${this.apiContext}/${id}/entity/${turConverseAgentEntity.id}`,
      JSON.stringify(turConverseAgentEntity));

  }
}
