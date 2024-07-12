import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { User } from '../_models';
import { Observable } from 'rxjs';
import {environment} from "../../../../../environments/environment";

@Injectable({ providedIn: 'root' })
export class UserService {
    constructor(private httpClient: HttpClient) { }

    getAll() {
        return this.httpClient.get<User>(`${environment.apiUrl}/api/v2/user/current`);
    }

    getStructure(): Observable<User> {
      return this.httpClient.get<User>(`${environment.apiUrl}/api/user/model`);
    }
}
