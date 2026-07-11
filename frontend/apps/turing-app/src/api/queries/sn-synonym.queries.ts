import { queryKeys } from "@/api/queries/keys";
import type {
  TurSNSynonym,
  TurSNSynonymAlgoliaImportRequest,
} from "@/models/sn/sn-site-synonym.model";
import { TurSNSiteSynonymService } from "@/services/sn/sn.site.synonym.service";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

const service = new TurSNSiteSynonymService();

export function useSynonyms(siteId: string | undefined, locale?: string) {
  return useQuery({
    queryKey: siteId ? queryKeys.synonyms.list(siteId) : ["synonyms", "pending"],
    queryFn: () => service.query(siteId as string, locale),
    enabled: Boolean(siteId),
  });
}

export function useSynonym(siteId: string | undefined, id: string | undefined) {
  return useQuery({
    queryKey: siteId && id ? queryKeys.synonyms.detail(siteId, id) : ["synonyms", "detail", "pending"],
    queryFn: () => service.get(siteId as string, id as string),
    enabled: Boolean(siteId) && Boolean(id) && id !== "new",
  });
}

export function useSynonymSupport(siteId: string | undefined) {
  return useQuery({
    queryKey: siteId ? queryKeys.synonyms.support(siteId) : ["synonyms", "support", "pending"],
    queryFn: () => service.support(siteId as string),
    enabled: Boolean(siteId),
  });
}

export function useSaveSynonym(siteId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (synonym: TurSNSynonym) =>
      synonym.id
        ? service.update(siteId, synonym.id, synonym)
        : service.create(siteId, synonym),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.synonyms.all() }),
  });
}

export function useDeleteSynonym(siteId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => service.delete(siteId, id),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.synonyms.all() }),
  });
}

export function useApplySynonyms(siteId: string) {
  return useMutation({
    mutationFn: (locale?: string) => service.apply(siteId, locale),
  });
}

export function useImportAlgoliaSynonyms(siteId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (request: TurSNSynonymAlgoliaImportRequest) =>
      service.importFromAlgolia(siteId, request),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.synonyms.all() }),
  });
}

/**
 * AI-assisted mining: fetch candidate sets from the search log and stage them
 * as disabled rules for the admin to review and enable (never auto-applied).
 */
export function useMineSynonyms(siteId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (locale: string) => {
      const proposals = await service.mine(siteId, locale);
      if (proposals.length > 0) {
        await service.batch(siteId, proposals);
      }
      return proposals;
    },
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: queryKeys.synonyms.all() }),
  });
}
