import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  ResearchConceptFit,
  ResearchDrift,
  ResearchGraph,
  ResearchReport,
  ResearchSaturation,
  ResearchStudy,
  ResearchStudyRequest,
  ResearchStudySummary,
} from "@/models/persona/persona-research.model";
import { TurPersonaResearchService } from "@/services/persona/persona-research.service";
import { queryKeys } from "./keys";

const service = new TurPersonaResearchService();

export { service as personaResearchService };

export function useResearchStudies() {
  return useQuery<ResearchStudySummary[]>({
    queryKey: queryKeys.personaResearch.list(),
    queryFn: () => service.list(),
  });
}

export function useResearchStudy(id: string | undefined) {
  return useQuery<ResearchStudy>({
    queryKey: id ? queryKeys.personaResearch.detail(id) : ["research-study", "pending"],
    queryFn: () => service.get(id as string),
    enabled: Boolean(id) && id !== "new",
  });
}

export function useResearchInsights(id: string | undefined, enabled: boolean) {
  return useQuery<ResearchReport>({
    queryKey: id ? queryKeys.personaResearch.insights(id) : ["research-study", "insights", "pending"],
    queryFn: () => service.insights(id as string),
    enabled: Boolean(id) && id !== "new" && enabled,
  });
}

export function useResearchSaturation(id: string | undefined, enabled: boolean) {
  return useQuery<ResearchSaturation>({
    queryKey: id ? queryKeys.personaResearch.saturation(id) : ["research-study", "saturation", "pending"],
    queryFn: () => service.saturation(id as string),
    enabled: Boolean(id) && id !== "new" && enabled,
  });
}

export function useResearchGraph(id: string | undefined, enabled: boolean) {
  return useQuery<ResearchGraph>({
    queryKey: id ? queryKeys.personaResearch.graph(id) : ["research-study", "graph", "pending"],
    queryFn: () => service.graph(id as string),
    enabled: Boolean(id) && id !== "new" && enabled,
  });
}

export function useResearchDrift(id: string | undefined, enabled: boolean) {
  return useQuery<ResearchDrift>({
    queryKey: id ? queryKeys.personaResearch.drift(id) : ["research-study", "drift", "pending"],
    queryFn: () => service.drift(id as string),
    enabled: Boolean(id) && id !== "new" && enabled,
  });
}

export function useResearchConceptFit(id: string | undefined, enabled: boolean) {
  return useQuery<ResearchConceptFit>({
    queryKey: id ? queryKeys.personaResearch.conceptFit(id) : ["research-study", "concept-fit", "pending"],
    queryFn: () => service.conceptFit(id as string),
    enabled: Boolean(id) && id !== "new" && enabled,
  });
}

function invalidateAll(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.personaResearch.all() });
}

export function useCreateResearchStudy() {
  const queryClient = useQueryClient();
  return useMutation<ResearchStudy, Error, ResearchStudyRequest>({
    mutationFn: (body) => service.create(body),
    onSuccess: () => invalidateAll(queryClient),
  });
}

export function useUpdateResearchStudy(id: string) {
  const queryClient = useQueryClient();
  return useMutation<ResearchStudy, Error, ResearchStudyRequest>({
    mutationFn: (body) => service.update(id, body),
    onSuccess: (saved) => {
      queryClient.setQueryData(queryKeys.personaResearch.detail(id), saved);
      invalidateAll(queryClient);
    },
  });
}

export function useDeleteResearchStudy() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, string>({
    mutationFn: (id) => service.remove(id),
    onSuccess: () => invalidateAll(queryClient),
  });
}

/** Refresh a study's detail + synthesized surfaces after a run/edit. */
export function invalidateResearchStudy(
  queryClient: ReturnType<typeof useQueryClient>,
  id: string,
) {
  queryClient.invalidateQueries({ queryKey: queryKeys.personaResearch.detail(id) });
  queryClient.invalidateQueries({ queryKey: queryKeys.personaResearch.insights(id) });
  queryClient.invalidateQueries({ queryKey: queryKeys.personaResearch.saturation(id) });
  queryClient.invalidateQueries({ queryKey: queryKeys.personaResearch.graph(id) });
  queryClient.invalidateQueries({ queryKey: queryKeys.personaResearch.drift(id) });
  queryClient.invalidateQueries({ queryKey: queryKeys.personaResearch.conceptFit(id) });
  queryClient.invalidateQueries({ queryKey: queryKeys.personaResearch.list() });
}
