import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import type {
  TurSkill,
  TurSkillInstallResponse,
  TurSkillManifest,
} from "@/models/skill/skill.model";
import { TurSkillService } from "@/services/skill/skill.service";

import { queryKeys } from "./keys";

const service = new TurSkillService();

/** Installed skills. */
export function useSkills() {
  return useQuery<TurSkill[]>({
    queryKey: queryKeys.skills.list(),
    queryFn: () => service.list(),
  });
}

/** Bundled platform catalog (the v1 skills available to install). */
export function useSkillCatalog() {
  return useQuery<TurSkillManifest[]>({
    queryKey: queryKeys.skills.catalog(),
    queryFn: () => service.catalog(),
  });
}

function invalidateSkills(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.skills.all() });
}

/** Install a bundled skill by manifest name. */
export function useInstallSkillFromCatalog() {
  const queryClient = useQueryClient();
  return useMutation<TurSkillInstallResponse, Error, string>({
    mutationFn: (name) => service.installFromCatalog(name),
    onSuccess: () => invalidateSkills(queryClient),
  });
}

/** Uninstall an installed skill by id (rejects built-ins server-side). */
export function useUninstallSkill() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, string>({
    mutationFn: (id) => service.uninstall(id),
    onSuccess: () => invalidateSkills(queryClient),
  });
}
