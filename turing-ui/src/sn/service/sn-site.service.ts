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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSN.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../environments/environment';
import { TurSNSite } from '../model/sn-site.model';

@Injectable()
export class TurSNSiteService {
    constructor(private httpClient: HttpClient) { }
    query(): Observable<TurSNSite[]> {
        return this.httpClient.get<TurSNSite[]>(`${environment.apiUrl}/api/sn`);
    }
    get(id: string): Observable<TurSNSite> {
        return this.httpClient.get<TurSNSite>(`${environment.apiUrl}/api/sn/${id}`);
    }

    public save(turSNSite: TurSNSite): Observable<Object> {
        return this.httpClient.put(`${environment.apiUrl}/api/sn/${turSNSite.id}`,
            JSON.stringify(turSNSite));

    }

}
