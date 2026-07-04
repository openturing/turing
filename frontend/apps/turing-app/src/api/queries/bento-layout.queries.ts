import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { BentoLayoutEntry, BentoLayoutResponse } from '@/components/bento/bento-layout';
import { TurBentoLayoutService } from '@/services/bento/bento-layout.service';
import { queryKeys } from './keys';

const service = new TurBentoLayoutService();

/**
 * T575 — the resolved layout (user override → global template → default) for a
 * list surface. Disabled until a `listId` is supplied so non-customizable lists
 * pay nothing.
 */
export function useBentoLayout(listId: string | undefined) {
  return useQuery<BentoLayoutResponse>({
    queryKey: listId ? queryKeys.bentoLayout.byList(listId) : ['bento-layout', 'pending'],
    queryFn: () => service.resolve(listId as string),
    enabled: Boolean(listId),
  });
}

interface SaveLayoutVars {
  listId: string;
  entries: BentoLayoutEntry[];
}

function seedLayout(
  queryClient: ReturnType<typeof useQueryClient>,
  saved: BentoLayoutResponse,
) {
  queryClient.setQueryData(queryKeys.bentoLayout.byList(saved.listId), saved);
}

/** Persist the current user's protective override. */
export function useSaveBentoLayout() {
  const queryClient = useQueryClient();
  return useMutation<BentoLayoutResponse, Error, SaveLayoutVars>({
    mutationFn: ({ listId, entries }) => service.saveUser(listId, entries),
    onSuccess: (saved) => seedLayout(queryClient, saved),
  });
}

/** Persist the admin global template (non-customizers inherit it). */
export function useSaveBentoLayoutGlobal() {
  const queryClient = useQueryClient();
  return useMutation<BentoLayoutResponse, Error, SaveLayoutVars>({
    mutationFn: ({ listId, entries }) => service.saveGlobal(listId, entries),
    onSuccess: (saved) => seedLayout(queryClient, saved),
  });
}

/** Clear the current user's override so they re-inherit the template/default. */
export function useResetBentoLayout() {
  const queryClient = useQueryClient();
  return useMutation<BentoLayoutResponse, Error, string>({
    mutationFn: (listId) => service.reset(listId),
    onSuccess: (saved) => seedLayout(queryClient, saved),
  });
}
