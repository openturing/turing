import { TurSNSearchPaginationItem } from "./sn-search-pagination-item.model";
import { TurSNSearchQueryContext } from "./sn-search-query-context.model";
import { TurSNSearchResults } from "./sn-search-results.model";
import { TurSNSearchWidget } from "./sn-search-widget.model";

export interface TurSNSearch {
  pagination: TurSNSearchPaginationItem[];
  queryContext: TurSNSearchQueryContext;
  results: TurSNSearchResults;
  widget: TurSNSearchWidget;
}
