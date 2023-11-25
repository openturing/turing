import {Pipe, PipeTransform} from '@angular/core';

@Pipe({
  name: 'sortByOrder'
})
export class SortByPipe implements PipeTransform {

  constructor() {
  }

  transform(value: any[],field: string, sort: string): any[] {
    if (sort.toLowerCase() === "asc") {
      return value.sort((n1, n2) => {
        return n2[field] - n1[field];
      });
    } else {
      return value.sort((n1, n2) => {
        return n1[field] - n2[field];
      });
    }
  }
}
