/**
 * T318 — React Query hooks over the skill catalog + folder editor.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { queryKeys } from "@/api/queries/keys";
import type {
  TurSkillSummary,
  TurSkillUiComponent,
} from "@/models/skill/skill.model";
import { TurSkillService } from "@/services/skill/skill.service";

const service = new TurSkillService();

export function useSkills() {
  return useQuery<TurSkillSummary[]>({
    queryKey: queryKeys.skills.list(),
    queryFn: () => service.query(),
  });
}

export function useSkill(id: string | undefined) {
  return useQuery<TurSkillSummary>({
    queryKey: id ? queryKeys.skills.detail(id) : ["skills", "pending"],
    queryFn: () => service.get(id as string),
    enabled: Boolean(id),
  });
}

export function useSkillFiles(id: string | undefined) {
  return useQuery({
    queryKey: id ? queryKeys.skills.files(id) : ["skills", "files", "pending"],
    queryFn: () => service.listFiles(id as string),
    enabled: Boolean(id),
  });
}

/** T449 — the UI components a skill ships, for registering generative renderers. */
export function useSkillUiComponents(id: string | undefined) {
  return useQuery<TurSkillUiComponent[]>({
    queryKey: id ? queryKeys.skills.uiComponents(id) : ["skills", "ui-components", "pending"],
    queryFn: () => service.uiComponents(id as string),
    enabled: Boolean(id),
  });
}

function invalidateSkills(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.skills.all() });
}

export function useCreateSkill() {
  const queryClient = useQueryClient();
  return useMutation<TurSkillSummary, Error, string>({
    mutationFn: (name) => service.create(name),
    onSuccess: () => invalidateSkills(queryClient),
  });
}

export function useImportSkillZip() {
  const queryClient = useQueryClient();
  return useMutation<string[], Error, File>({
    mutationFn: (file) => service.importZip(file),
    onSuccess: () => invalidateSkills(queryClient),
  });
}

export function useSetSkillEnabled() {
  const queryClient = useQueryClient();
  return useMutation<TurSkillSummary, Error, { id: string; value: boolean }>({
    mutationFn: ({ id, value }) => service.setEnabled(id, value),
    onSuccess: () => invalidateSkills(queryClient),
  });
}
