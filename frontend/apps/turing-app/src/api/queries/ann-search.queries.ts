import { useQuery } from "@tanstack/react-query";
import {
  TurAnnSearchService,
  type TurAnnSearchRequest,
  type TurAnnSearchResponse,
} from "@/services/ann/ann.search.service";
import { queryKeys } from "./keys";

const service = new TurAnnSearchService();

/**
 * T1018 — one vector search, keyed by the site and the request.
 *
 * The URL is the source of truth for that request, so paging back to a page
 * already seen is answered from cache instead of re-searched. Failures are not
 * retried: the three the page distinguishes — 403, 404 and everything else —
 * are all answers rather than glitches.
 */
export function useAnnSearch(site: string, request: TurAnnSearchRequest) {
  return useQuery<TurAnnSearchResponse>({
    queryKey: queryKeys.annSearch.query(site, request),
    queryFn: () => service.search(site, request),
    enabled: Boolean(site),
    retry: 0,
  });
}
