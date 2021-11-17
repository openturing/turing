import { TurSNSearchDefaultFields } from "./sn-search-default-fields.model";

export interface TurSNSearchQuery {
  queryString: string;
  sort: string;
  locale: string;
}
