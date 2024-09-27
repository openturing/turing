import initializeAxios from './axiosSetup';
import { axiosRequestConfiguration } from './config';
import { map } from 'rxjs/operators';
import {defer, Observable} from 'rxjs';


const axiosInstance = initializeAxios(axiosRequestConfiguration);

const getAll = <T>(url: string, queryParams?: object): Observable<T> => {
    return defer(()=> axiosInstance.get<T>(url, { params: queryParams }))
        .pipe(map(result => result.data));
};
const getById = <T>(url: string, id:string | undefined): Observable<T> => {
    return defer(()=> axiosInstance.get<T>(`${url}/${id}`))
        .pipe(map(result => result.data));
};

const post = <T>(url: string, body: object, queryParams?: object): Observable<T | void> => {
    return defer(()=> axiosInstance.post<T>(url, body, { params: queryParams }))
        .pipe(map(result => result.data));
};

const put = <T>(url: string, body: object, queryParams?: object): Observable<T | void> => {
    return defer(()=>axiosInstance.put<T>(url, body, { params: queryParams }))
        .pipe(map(result => result.data));
};

const deleteById = <T>(url: string, id:string): Observable<T | void> => {
    return defer(() => (axiosInstance.delete(`${url}/${id}` )))
        .pipe(map(result => result.data)
        );
};

export default { getAll, getById, post, put, delete: deleteById };
