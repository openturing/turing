import { queryKeys } from "@/api/queries/keys";
import type { TurSNSiteCustomFacet } from "@/models/sn/sn-site-custom-facet.model";
import { TurSNSiteCustomFacetService } from "@/services/sn/sn.site.custom.facet.service";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

const service = new TurSNSiteCustomFacetService();

/** T1018 — every custom facet declared on one SN site. */
export function useSnCustomFacets(siteId: string | undefined) {
  return useQuery<TurSNSiteCustomFacet[]>({
    queryKey: siteId ? queryKeys.snCustomFacets.list(siteId) : ["sn-custom-facets", "pending"],
    queryFn: () => service.query(siteId as string),
    enabled: Boolean(siteId),
  });
}

/**
 * T1018 — one custom facet. Disabled for the `new` route, where the detail page
 * seeds an empty record rather than asking the server for one.
 */
export function useSnCustomFacet(siteId: string | undefined, id: string | undefined) {
  return useQuery<TurSNSiteCustomFacet>({
    queryKey:
      siteId && id
        ? queryKeys.snCustomFacets.detail(siteId, id)
        : ["sn-custom-facets", "detail", "pending"],
    queryFn: () => service.get(siteId as string, id as string),
    enabled: Boolean(siteId) && Boolean(id) && id !== "new",
  });
}

/**
 * T1018 — delete one custom facet, invalidating the site's list.
 *
 * The page used to call the service directly and navigate, which left whatever
 * the list had cached showing the row that had just gone.
 */
export function useDeleteSnCustomFacet(siteId: string) {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, string>({
    mutationFn: (id) => service.delete(siteId, id),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.snCustomFacets.all() }),
  });
}
