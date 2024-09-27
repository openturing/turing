import {Observable} from "rxjs";

export interface TurListProps<T>  {
    getAll: (url: string) => Observable<Array<T>>;
    change: Observable<boolean> | undefined;
}
