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
import { TurNLPEntity } from '../model/nlp-entity.model';

@Injectable()
export class TurNLPEntityService {
    constructor(private httpClient: HttpClient) { }
    query(): Observable<TurNLPEntity[]> {
        return this.httpClient.get<TurNLPEntity[]>(`${environment.apiUrl}/api/entity`);
    }
    get(id: string): Observable<TurNLPEntity> {
        return this.httpClient.get<TurNLPEntity>(`${environment.apiUrl}/api/entity/${id}`);
    }

    public save(turNLPEntity: TurNLPEntity): Observable<Object> {
        return this.httpClient.put(`${environment.apiUrl}/api/entity/${turNLPEntity.id}`,
            JSON.stringify(turNLPEntity));

    }

}
