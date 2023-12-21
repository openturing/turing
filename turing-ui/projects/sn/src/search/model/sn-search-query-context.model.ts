import { TurSNSearchDefaultFields } from "./sn-search-default-fields.model";
import { TurSNSearchQuery } from "./sn-search-query.model";

export interface TurSNSearchQueryContext {
  count: number;
  defaultFields: TurSNSearchDefaultFields;
  index: string;
  limit: number;
  offset: number;
  page: number;
  pageCount: number;
  pageEnd: number;
  pageStart: number;
  query: TurSNSearchQuery;
  responseTime: number;
  facetType: string;
}
