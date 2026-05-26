import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurCustomTool } from '@/models/customtool/custom-tool.model.ts';
import type { CustomToolEditorDescriptor } from '@/models/customtool/custom-tool-descriptor.model.ts';
import {
  TurCustomToolService,
  type DraftClearResponse,
  type DraftPutResponse,
  type DraftStatusResponse,
} from '@/services/customtool/custom-tool.service';
import { queryKeys } from './keys';

const service = new TurCustomToolService();

export function useCustomTools() {
  return useQuery<TurCustomTool[]>({
    queryKey: queryKeys.customTools.list(),
    queryFn: () => service.query(),
  });
}

/**
 * T40: descriptor is a static catalogue at runtime (the binding set isn't
 * dynamic), so we treat it as immutable for the session — `staleTime: Infinity`
 * means the editor never re-fetches mid-edit.
 */
export function useCustomToolDescriptor() {
  return useQuery<CustomToolEditorDescriptor>({
    queryKey: queryKeys.customTools.descriptor(),
    queryFn: () => service.getDescriptor(),
    staleTime: Infinity,
    gcTime: Infinity,
  });
}

function invalidateCustomTools(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.customTools.all() });
}

export function useCreateCustomTool() {
  const queryClient = useQueryClient();
  return useMutation<TurCustomTool, Error, TurCustomTool>({
    mutationFn: (tool) => service.create(tool),
    onSuccess: () => invalidateCustomTools(queryClient),
  });
}

export function useUpdateCustomTool() {
  const queryClient = useQueryClient();
  return useMutation<TurCustomTool, Error, TurCustomTool>({
    mutationFn: (tool) => service.update(tool),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.customTools.detail(saved.id), saved);
      }
      invalidateCustomTools(queryClient);
    },
  });
}

export function useDeleteCustomTool() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, TurCustomTool>({
    mutationFn: (tool) => service.delete(tool),
    onSuccess: (_ok, tool) => {
      if (tool?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.customTools.detail(tool.id) });
      }
      invalidateCustomTools(queryClient);
    },
  });
}

/**
 * T41: current admin's live-preview draft status for a given tool. Hits the
 * server on mount; refetched after every `usePushCustomToolDraft` /
 * `useClearCustomToolDraft` mutation so the UI banner stays in sync.
 */
export function useCustomToolDraftStatus(id: string | undefined) {
  return useQuery<DraftStatusResponse>({
    queryKey: id ? queryKeys.customTools.draft(id) : ['custom-tools', 'draft', 'pending'],
    queryFn: () => service.getDraft(id as string),
    enabled: Boolean(id),
  });
}

export function usePushCustomToolDraft(id: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation<DraftPutResponse, Error, string>({
    mutationFn: (groovyScript) => service.putDraft(id as string, groovyScript),
    onSuccess: () => {
      if (id) {
        queryClient.invalidateQueries({ queryKey: queryKeys.customTools.draft(id) });
      }
    },
  });
}

export function useClearCustomToolDraft(id: string | undefined) {
  const queryClient = useQueryClient();
  return useMutation<DraftClearResponse, Error, void>({
    mutationFn: () => service.clearDraft(id as string),
    onSuccess: () => {
      if (id) {
        queryClient.invalidateQueries({ queryKey: queryKeys.customTools.draft(id) });
      }
    },
  });
}
