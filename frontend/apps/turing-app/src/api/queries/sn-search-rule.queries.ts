import { queryKeys } from "@/api/queries/keys";
import type { TurSNSiteSearchRule } from "@/models/sn/sn-site-search-rule.model";
import { TurSNSiteSearchRuleService } from "@/services/sn/sn.site.search.rule.service";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

const service = new TurSNSiteSearchRuleService();

/** T1018 — every search rule declared on one SN site. */
export function useSnSearchRules(siteId: string | undefined) {
  return useQuery<TurSNSiteSearchRule[]>({
    queryKey: siteId ? queryKeys.snSearchRules.list(siteId) : ["sn-search-rules", "pending"],
    queryFn: () => service.query(siteId as string),
    enabled: Boolean(siteId),
  });
}

/**
 * T1018 — one search rule. Disabled for the `new` route, where the detail page
 * seeds an empty record rather than asking the server for one.
 */
export function useSnSearchRule(siteId: string | undefined, id: string | undefined) {
  return useQuery<TurSNSiteSearchRule>({
    queryKey:
      siteId && id
        ? queryKeys.snSearchRules.detail(siteId, id)
        : ["sn-search-rules", "detail", "pending"],
    queryFn: () => service.get(siteId as string, id as string),
    enabled: Boolean(siteId) && Boolean(id) && id !== "new",
  });
}

/**
 * T1018 — delete one search rule, invalidating the site's list.
 *
 * The page used to call the service directly and navigate, which left whatever
 * the list had cached showing the row that had just gone.
 */
export function useDeleteSnSearchRule(siteId: string) {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, string>({
    mutationFn: (id) => service.delete(siteId, id),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.snSearchRules.all() }),
  });
}
