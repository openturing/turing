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

/** Full manifest of an installed skill — prefills the CRUD edit form. */
export function useSkill(id: string | undefined) {
  return useQuery<TurSkillManifest>({
    queryKey: id ? queryKeys.skills.detail(id) : ["skills", "detail", "pending"],
    queryFn: () => service.get(id as string),
    enabled: Boolean(id),
  });
}

function invalidateSkills(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.skills.all() });
}

/** Create or update a user-authored skill from a manifest (upserts by name+version). */
export function useInstallSkill() {
  const queryClient = useQueryClient();
  return useMutation<TurSkillInstallResponse, Error, TurSkillManifest>({
    mutationFn: (manifest) => service.install(manifest),
    onSuccess: () => invalidateSkills(queryClient),
  });
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
