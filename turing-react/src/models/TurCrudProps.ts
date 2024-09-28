import {Observable} from "rxjs";

export interface TurCrudProps<T>  {
    get: (url: string, id: string | undefined) => Observable<T>;
    delete: (url: string, id: string) => void
    add: (url: string, item: T) => void;
    update: (url: string, item: T) => void;
    change: Observable<boolean> | undefined;
}
