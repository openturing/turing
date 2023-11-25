import { Pipe, PipeTransform } from '@angular/core';

@Pipe({
  name: 'fullTextSearch',
  pure: false
})
export class FullTextSearchPipe implements PipeTransform {

  constructor() { }

  transform(value: any, query: string, field: string): any {
    return query ? value.reduce((prev: any[], next: { [x: string]: string | string[]; }) => {
      if (next[field].includes(query)) { prev.push(next); }
      return prev;
    }, []) : value;
  }
}
