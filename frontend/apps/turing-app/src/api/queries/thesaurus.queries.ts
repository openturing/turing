import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  TurKnowledgeBase,
  TurMicrothesaurus,
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
