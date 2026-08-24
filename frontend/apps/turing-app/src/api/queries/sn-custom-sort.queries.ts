import { queryKeys } from "@/api/queries/keys";
import type { TurSNSiteCustomSort } from "@/models/sn/sn-site-custom-sort.model";
import { TurSNSiteCustomSortService } from "@/services/sn/sn.site.custom.sort.service";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

const service = new TurSNSiteCustomSortService();

/** T1018 — every custom sort declared on one SN site. */
export function useSnCustomSorts(siteId: string | undefined) {
  return useQuery<TurSNSiteCustomSort[]>({
    queryKey: siteId ? queryKeys.snCustomSorts.list(siteId) : ["sn-custom-sorts", "pending"],
    queryFn: () => service.query(siteId as string),
    enabled: Boolean(siteId),
  });
}

/**
 * T1018 — one custom sort. Disabled for the `new` route, where the detail page
 * seeds an empty record rather than asking the server for one.
 */
export function useSnCustomSort(siteId: string | undefined, id: string | undefined) {
  return useQuery<TurSNSiteCustomSort>({
    queryKey:
      siteId && id
        ? queryKeys.snCustomSorts.detail(siteId, id)
        : ["sn-custom-sorts", "detail", "pending"],
    queryFn: () => service.get(siteId as string, id as string),
    enabled: Boolean(siteId) && Boolean(id) && id !== "new",
  });
}

/**
 * T1018 — delete one custom sort, invalidating the site's list.
 *
 * The page used to call the service directly and navigate, which left whatever
 * the list had cached showing the row that had just gone.
 */
export function useDeleteSnCustomSort(siteId: string) {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, string>({
    mutationFn: (id) => service.delete(siteId, id),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.snCustomSorts.all() }),
  });
}
