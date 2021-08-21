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
import { TurNLPVendor } from '../model/nlp-vendor.model';

@Injectable()
export class TurNLPVendorService {
    constructor(private httpClient: HttpClient) { }
    query(): Observable<TurNLPVendor[]> {
        return this.httpClient.get<TurNLPVendor[]>(`${environment.apiUrl}/api/nlp/vendor`);
    }
    get(id: string): Observable<TurNLPVendor> {
        return this.httpClient.get<TurNLPVendor>(`${environment.apiUrl}/api/nlp/vendor/${id}`);
    }

    public save(turNLPVendor: TurNLPVendor): Observable<Object> {
        return this.httpClient.put(`${environment.apiUrl}/api/nlp/vendor/${turNLPVendor.id}`,
            JSON.stringify(turNLPVendor));

    }

}
