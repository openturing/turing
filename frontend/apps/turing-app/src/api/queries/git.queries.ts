import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { TurGitRepository } from "@/models/git/git-repository.model";
import { TurGitService, type TurGitBranch, type TurGitTreeEntry } from "@/services/git/git.service";
import { queryKeys } from "./keys";

const service = new TurGitService();

/** T1018 — every repository this instance hosts. */
export function useGitRepositories() {
  return useQuery<TurGitRepository[]>({
    queryKey: queryKeys.git.repositories(),
    queryFn: () => service.list(),
  });
}

/** T1018 — one repository's branches. Empty for a repo with no commits yet. */
export function useGitBranches(name: string | undefined) {
  return useQuery<TurGitBranch[]>({
    queryKey: name ? queryKeys.git.branches(name) : ["git", "branches", "pending"],
    queryFn: () => service.listBranches(name as string),
    enabled: Boolean(name),
  });
}

/**
 * T1018 — one directory listing, identified by repository, ref and path.
 *
 * Disabled until a branch is resolved, which is what the page's
 * `if (currentBranch) loadTree()` guard did — and because the path is part of
 * the key, walking back up a tree is served from cache.
 */
export function useGitTree(name: string | undefined, ref: string, path: string) {
  return useQuery<TurGitTreeEntry[]>({
    queryKey: name ? queryKeys.git.tree(name, ref, path) : ["git", "tree", "pending"],
    queryFn: () => service.listTree(name as string, ref, path || undefined),
    enabled: Boolean(name) && Boolean(ref),
  });
}

/** T1018 — create a repository, refreshing the list it joins. */
export function useCreateGitRepository() {
  const queryClient = useQueryClient();
  return useMutation<TurGitRepository, Error, string>({
    mutationFn: (name) => service.create(name),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.git.all() }),
  });
}

/** T1018 — delete a repository, refreshing the list it leaves. */
export function useDeleteGitRepository() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, string>({
    mutationFn: (name) => service.delete(name),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: queryKeys.git.all() }),
  });
}
