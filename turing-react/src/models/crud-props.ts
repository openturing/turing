import {Observable} from "rxjs";

export interface CrudProps<T>  {
    get: (url: string) => Observable<Array<T>>;
    delete: (url: string, id: string) => void
    add: (url: string, item: T) => void;
    update: (url: string, item: T) => void;
    change: Observable<boolean> | undefined;
}
