import {Pipe, PipeTransform} from '@angular/core';

@Pipe({
  name: 'fullTextSearch',
  pure: false
})
export class FullTextSearchPipe implements PipeTransform {

  constructor() {
  }

  transform(value: any, query: string, field: string): any {
    return query ? value.reduce((prev: any[], next: { [x: string]: string; }) => {
      if (next[field].toLowerCase().includes(query.toLowerCase())) {
        prev.push(next);
      }
      return prev;
    }, []) : value;
  }
}
