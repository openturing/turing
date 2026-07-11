import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  TurKnowledgeBase,
  TurMicrothesaurus,
  TurSNSiteMicrothesaurus,
  TurSNSiteMicrothesaurusConfig,
  TurThesaurusDraft,
  TurThesaurusGenerationRequest,
  TurThesaurusSeed,
  TurThesaurusTerm,
} from "@/models/kb/thesaurus.model";
import { TurThesaurusService } from "@/services/kb/thesaurus.service";
import { queryKeys } from "./keys";

const service = new TurThesaurusService();

// ---- Knowledge Base (library) ----
export function useKnowledgeBases() {
  return useQuery<TurKnowledgeBase[]>({
    queryKey: queryKeys.thesaurus.kbList(),
    queryFn: () => service.listKnowledgeBases(),
  });
}

export function useKnowledgeBase(id: string | undefined) {
  return useQuery<TurKnowledgeBase>({
    queryKey: id ? queryKeys.thesaurus.kbDetail(id) : ["thesaurus", "kb", "pending"],
    queryFn: () => service.getKnowledgeBase(id as string),
    enabled: Boolean(id),
  });
}

export function useCreateKnowledgeBase() {
  const qc = useQueryClient();
  return useMutation<TurKnowledgeBase, Error, TurKnowledgeBase>({
    mutationFn: (kb) => service.createKnowledgeBase(kb),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.thesaurus.all() }),
  });
}

export function useUpdateKnowledgeBase() {
  const qc = useQueryClient();
  return useMutation<TurKnowledgeBase, Error, TurKnowledgeBase>({
    mutationFn: (kb) => service.updateKnowledgeBase(kb),
    onSuccess: (saved) => {
      if (saved?.id) qc.setQueryData(queryKeys.thesaurus.kbDetail(saved.id), saved);
      qc.invalidateQueries({ queryKey: queryKeys.thesaurus.kbList() });
    },
  });
}

export function useDeleteKnowledgeBase() {
  const qc = useQueryClient();
  return useMutation<boolean, Error, TurKnowledgeBase>({
    mutationFn: (kb) => service.deleteKnowledgeBase(kb),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.thesaurus.all() }),
  });
}

// ---- Microthesaurus ----
export function useMicrothesauri(kbId: string | undefined) {
  return useQuery<TurMicrothesaurus[]>({
    queryKey: kbId ? queryKeys.thesaurus.microList(kbId) : ["thesaurus", "micro", "pending"],
    queryFn: () => service.listMicrothesauri(kbId as string),
    enabled: Boolean(kbId),
  });
}

export function useMicrothesaurus(kbId: string | undefined, id: string | undefined) {
  return useQuery<TurMicrothesaurus>({
    queryKey:
      kbId && id
        ? queryKeys.thesaurus.microDetail(kbId, id)
        : ["thesaurus", "micro", "detail", "pending"],
    queryFn: () => service.getMicrothesaurus(kbId as string, id as string),
    enabled: Boolean(kbId && id),
  });
}

export function useCreateMicrothesaurus(kbId: string) {
  const qc = useQueryClient();
  return useMutation<TurMicrothesaurus, Error, TurMicrothesaurus>({
    mutationFn: (m) => service.createMicrothesaurus(kbId, m),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.thesaurus.microList(kbId) }),
  });
}

export function useUpdateMicrothesaurus(kbId: string) {
  const qc = useQueryClient();
  return useMutation<TurMicrothesaurus, Error, TurMicrothesaurus>({
    mutationFn: (m) => service.updateMicrothesaurus(kbId, m),
    onSuccess: (saved) => {
      if (saved?.id) qc.setQueryData(queryKeys.thesaurus.microDetail(kbId, saved.id), saved);
      qc.invalidateQueries({ queryKey: queryKeys.thesaurus.microList(kbId) });
    },
  });
}

export function useDeleteMicrothesaurus(kbId: string) {
  const qc = useQueryClient();
  return useMutation<boolean, Error, string>({
    mutationFn: (id) => service.deleteMicrothesaurus(kbId, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.thesaurus.microList(kbId) }),
  });
}

// ---- Terms ----
export function useThesaurusTerms(microId: string | undefined) {
  return useQuery<TurThesaurusTerm[]>({
    queryKey: microId ? queryKeys.thesaurus.termList(microId) : ["thesaurus", "term", "pending"],
    queryFn: () => service.listTerms(microId as string),
    enabled: Boolean(microId),
  });
}

export function useCreateTerm(microId: string) {
  const qc = useQueryClient();
  return useMutation<TurThesaurusTerm, Error, TurThesaurusTerm>({
    mutationFn: (term) => service.createTerm(microId, term),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.thesaurus.termList(microId) }),
  });
}

export function useUpdateTerm(microId: string) {
  const qc = useQueryClient();
  return useMutation<TurThesaurusTerm, Error, TurThesaurusTerm>({
    mutationFn: (term) => service.updateTerm(microId, term),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.thesaurus.termList(microId) }),
  });
}

export function useDeleteTerm(microId: string) {
  const qc = useQueryClient();
  return useMutation<boolean, Error, string>({
    mutationFn: (id) => service.deleteTerm(microId, id),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.thesaurus.termList(microId) }),
  });
}

// ---- Seed library ----
export function useThesaurusSeeds() {
  return useQuery<TurThesaurusSeed[]>({
    queryKey: queryKeys.thesaurus.seeds(),
    queryFn: () => service.listSeeds(),
  });
}

export function useImportSeed(kbId: string) {
  const qc = useQueryClient();
  return useMutation<TurMicrothesaurus, Error, string>({
    mutationFn: (seedId) => service.importSeed(kbId, seedId),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.thesaurus.microList(kbId) }),
  });
}

export function useUploadAuthorityFile(kbId: string) {
  const qc = useQueryClient();
  return useMutation<TurMicrothesaurus, Error, { file: File; domain?: string }>({
    mutationFn: ({ file, domain }) => service.uploadAuthorityFile(kbId, file, domain),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.thesaurus.microList(kbId) }),
  });
}

// ---- LLM-assisted generation (T675) ----
export function useGenerateThesaurusDraft(kbId: string) {
  return useMutation<TurThesaurusDraft, Error, TurThesaurusGenerationRequest>({
    mutationFn: (request) => service.generateDraft(kbId, request),
  });
}

export function useCreateFromDraft(kbId: string) {
  const qc = useQueryClient();
  return useMutation<TurMicrothesaurus, Error, TurThesaurusDraft>({
    mutationFn: (draft) => service.createFromDraft(kbId, draft),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.thesaurus.microList(kbId) }),
  });
}

// ---- Per-SN-site selection + config (T670 / T681) ----
export function useSiteSelections(snSiteId: string | undefined) {
  return useQuery<TurSNSiteMicrothesaurus[]>({
    queryKey: snSiteId
      ? queryKeys.thesaurus.siteSelections(snSiteId)
      : ["thesaurus", "site", "selections", "pending"],
    queryFn: () => service.listSiteSelections(snSiteId as string),
    enabled: Boolean(snSiteId),
  });
}

export function useSiteConfig(snSiteId: string | undefined) {
  return useQuery<TurSNSiteMicrothesaurusConfig>({
    queryKey: snSiteId
      ? queryKeys.thesaurus.siteConfig(snSiteId)
      : ["thesaurus", "site", "config", "pending"],
    queryFn: () => service.getSiteConfig(snSiteId as string),
    enabled: Boolean(snSiteId),
  });
}

export function useSelectForSite(snSiteId: string) {
  const qc = useQueryClient();
  return useMutation<TurSNSiteMicrothesaurus, Error, string>({
    mutationFn: (microthesaurusId) => service.selectForSite(snSiteId, microthesaurusId),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.thesaurus.siteSelections(snSiteId) }),
  });
}

export function useSetSiteSelectionEnabled(snSiteId: string) {
  const qc = useQueryClient();
  return useMutation<TurSNSiteMicrothesaurus, Error, { selectionId: string; enabled: boolean }>({
    mutationFn: ({ selectionId, enabled }) =>
      service.setSiteSelectionEnabled(snSiteId, selectionId, enabled),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.thesaurus.siteSelections(snSiteId) }),
  });
}

export function useDeselectForSite(snSiteId: string) {
  const qc = useQueryClient();
  return useMutation<boolean, Error, string>({
    mutationFn: (selectionId) => service.deselectForSite(snSiteId, selectionId),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: queryKeys.thesaurus.siteSelections(snSiteId) }),
  });
}

export function useSaveSiteConfig(snSiteId: string) {
  const qc = useQueryClient();
  return useMutation<TurSNSiteMicrothesaurusConfig, Error, TurSNSiteMicrothesaurusConfig>({
    mutationFn: (config) => service.saveSiteConfig(snSiteId, config),
    onSuccess: (saved) => {
      qc.setQueryData(queryKeys.thesaurus.siteConfig(snSiteId), saved);
    },
  });
}
