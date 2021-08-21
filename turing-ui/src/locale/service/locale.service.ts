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
import { TurLocale } from '../model/locale.model';

@Injectable()
export class TurLocaleService {
    constructor(private httpClient: HttpClient) { }
    query(): Observable<TurLocale[]> {
        return this.httpClient.get<TurLocale[]>(`${environment.apiUrl}/api/locale`);
    }
    get(initials: string): Observable<TurLocale> {
        return this.httpClient.get<TurLocale>(`${environment.apiUrl}/api/locale/${initials}`);
    }

    public save(turLocale: TurLocale): Observable<Object> {
        return this.httpClient.put(`${environment.apiUrl}/api/locale/${turLocale.initials}`,
            JSON.stringify(turLocale));

    }

}
