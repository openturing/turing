/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import {Observable, of, Subject} from "rxjs";
import {TurNLPInstance} from "../models/nlp-instance.model";
import api from "../axios/api";
import {catchError, take} from "rxjs/operators";

export const useObservable = () => {
    const subj = new Subject<boolean>();

    const next = (value: boolean): void => {
        subj.next(value)
    };

    return {change: subj.asObservable(), next};
};

export default function nlpInstanceService() {
    const {change: engineerChange, next: engNext} = useObservable();
    const {next: lanNext} = useObservable();

    const getAll = <TurNLPInstance, >(url: string): Observable<TurNLPInstance[]> => {
        return api.getAll<TurNLPInstance[]>(url)
            .pipe(
                take(1),
                catchError(err => of(console.log(err)))
            ) as Observable<TurNLPInstance[]>;
    };
    const getItem = (url: string, id: string | undefined): Observable<TurNLPInstance> => {
        return api.getById<TurNLPInstance>(url, id)
            .pipe(
                take(1),
                catchError(err => of(console.log(err)))
            ) as Observable<TurNLPInstance>;
    };
    const addItem = (url: string, item: (TurNLPInstance)): void => {
        api.post(url, item)
            .pipe(take(1))
            .subscribe(() => {
                url === 'nlp' ? engNext(true) : lanNext(true);
            });
    };

    const updateItem = (url: string, item: (TurNLPInstance)) => {
        api.put(url, item)
            .pipe(take(1))
            .subscribe(() => {
                url === 'nlp' ? engNext(true) : lanNext(true);
            });
    };

    const deleteItem = (url: string, id: string): void => {
        api.delete(url, id)
            .subscribe(() => {
                url === 'nlp' ? engNext(true) : lanNext(true);
            });
    };
    return {engineerChange, getAll, getItem, addItem, updateItem, deleteItem};
}
