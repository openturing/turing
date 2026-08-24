import { useQuery } from "@tanstack/react-query";
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model";
import { TurSNFieldService } from "@/services/sn/sn.field.service";
import { queryKeys } from "./keys";

const service = new TurSNFieldService();

/** T1018 — every field declared on one SN site. */
export function useSnFields(siteId: string | undefined) {
  return useQuery<TurSNSiteField[]>({
    queryKey: siteId ? queryKeys.snFields.list(siteId) : ["sn-fields", "pending"],
    queryFn: () => service.query(siteId as string),
    enabled: Boolean(siteId),
  });
}

/**
 * T1018 — the engine's view of those fields, which is a second call.
 *
 * Its own entry rather than folded into the list: the two answer different
 * questions and the list page renders neither until both arrive.
 */
export function useSnFieldStatus(siteId: string | undefined) {
  return useQuery({
    queryKey: siteId ? queryKeys.snFields.status(siteId) : ["sn-fields", "status", "pending"],
    queryFn: () => service.getStatusFields(siteId as string),
    enabled: Boolean(siteId),
  });
}
