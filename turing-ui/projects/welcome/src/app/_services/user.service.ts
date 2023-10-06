import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';

import { environment } from '../../environments/environment';
import { User } from '../_models';
import { Observable } from 'rxjs';

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
