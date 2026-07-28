import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  MatchAdjustments,
  MatchDrift,
  MatchMatrix,
  MatchProject,
  MatchProjectRequest,
  MatchProjectSummary,
  MatchTrend,
} from "@/models/persona/persona-match.model";
import { TurPersonaMatchService } from "@/services/persona/persona-match.service";
import { queryKeys } from "./keys";

const service = new TurPersonaMatchService();

export { service as personaMatchService };

export function useMatchProjects() {
  return useQuery<MatchProjectSummary[]>({
    queryKey: queryKeys.personaMatch.list(),
    queryFn: () => service.list(),
  });
}

export function useMatchProject(id: string | undefined) {
  return useQuery<MatchProject>({
    queryKey: id ? queryKeys.personaMatch.detail(id) : ["persona-match", "pending"],
    queryFn: () => service.get(id as string),
    enabled: Boolean(id) && id !== "new",
  });
}

export function useMatchMatrix(id: string | undefined) {
  return useQuery<MatchMatrix>({
    queryKey: id ? queryKeys.personaMatch.matrix(id) : ["persona-match", "matrix", "pending"],
    queryFn: () => service.matrix(id as string),
    enabled: Boolean(id) && id !== "new",
  });
}

/** T704 — the per-cell fit series + run-average trend. */
export function useMatchTrend(id: string | undefined) {
  return useQuery<MatchTrend>({
    queryKey: id ? queryKeys.personaMatch.trend(id) : ["persona-match", "trend", "pending"],
    queryFn: () => service.trend(id as string),
    enabled: Boolean(id) && id !== "new",
  });
}

/** T704 — how fit moved between the last two runs. */
export function useMatchDrift(id: string | undefined) {
  return useQuery<MatchDrift>({
    queryKey: id ? queryKeys.personaMatch.drift(id) : ["persona-match", "drift", "pending"],
    queryFn: () => service.drift(id as string),
    enabled: Boolean(id) && id !== "new",
  });
}

/**
 * T704 — LLM-suggested persona/content adjustments. A mutation rather than a
 * query because it is an explicit, possibly token-spending action.
 */
export function useMatchAdjustments(id: string | undefined) {
  return useMutation<MatchAdjustments, Error, boolean | undefined>({
    mutationFn: (regenerate) => service.adjustments(id as string, regenerate ?? false),
  });
}

function invalidateAll(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.personaMatch.all() });
}

export function useCreateMatchProject() {
  const queryClient = useQueryClient();
  return useMutation<MatchProject, Error, MatchProjectRequest>({
    mutationFn: (body) => service.create(body),
    onSuccess: () => invalidateAll(queryClient),
  });
}

export function useUpdateMatchProject(id: string) {
  const queryClient = useQueryClient();
  return useMutation<MatchProject, Error, MatchProjectRequest>({
    mutationFn: (body) => service.update(id, body),
    onSuccess: (saved) => {
      queryClient.setQueryData(queryKeys.personaMatch.detail(id), saved);
      invalidateAll(queryClient);
    },
  });
}

export function useDeleteMatchProject() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, string>({
    mutationFn: (id) => service.remove(id),
    onSuccess: () => invalidateAll(queryClient),
  });
}

/** Refresh a project's detail + matrix after a source/persona/run change. */
export function invalidateMatchProject(
  queryClient: ReturnType<typeof useQueryClient>,
  id: string,
) {
  queryClient.invalidateQueries({ queryKey: queryKeys.personaMatch.detail(id) });
  queryClient.invalidateQueries({ queryKey: queryKeys.personaMatch.matrix(id) });
  queryClient.invalidateQueries({ queryKey: queryKeys.personaMatch.trend(id) });
  queryClient.invalidateQueries({ queryKey: queryKeys.personaMatch.drift(id) });
  queryClient.invalidateQueries({ queryKey: queryKeys.personaMatch.list() });
}
