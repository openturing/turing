import { useQuery, useQueryClient } from "@tanstack/react-query";
import type { TurPageSite } from "@/models/page/page-site.model";
import { TurPageService } from "@/services/page/page.service";
import { queryKeys } from "./keys";

const service = new TurPageService();

/** T1018 — the static sites the page manager serves. */
export function usePageSites() {
  return useQuery<TurPageSite[]>({
    queryKey: queryKeys.pages.list(),
    queryFn: () => service.query(),
  });
}

/**
 * T1018 — refresh the listing after an upload or a delete.
 *
 * The page called its own loader after each write; this is the same refresh,
 * expressed once so anything else reading the list follows.
 */
export function useInvalidatePageSites() {
  const queryClient = useQueryClient();
  return () => queryClient.invalidateQueries({ queryKey: queryKeys.pages.all() });
}
