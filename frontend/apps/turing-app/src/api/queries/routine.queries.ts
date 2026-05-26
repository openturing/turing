import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurRoutine } from '@/models/genai/routine.model';
import { TurRoutineService } from '@/services/genai/routine.service';
import { queryKeys } from './keys';

/**
 * React Query hooks for the deployment-wide routine catalog. Routines are
 * fired by {@code scheduleAgent} chat-flow nodes; the editor uses
 * {@link useRoutines} to power the routine dropdown.
 *
 * @since 2026.3.1
 */
const service = new TurRoutineService();

export function useRoutines() {
  return useQuery<TurRoutine[]>({
    queryKey: queryKeys.routines.list(),
    queryFn: () => service.query(),
  });
}

export function useRoutine(id: string | undefined) {
  return useQuery<TurRoutine>({
    queryKey: id ? queryKeys.routines.detail(id) : ['routines', 'detail', 'pending'],
    queryFn: () => service.get(id as string),
    enabled: Boolean(id),
  });
}

function invalidate(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.routines.all() });
}

export function useCreateRoutine() {
  const queryClient = useQueryClient();
  return useMutation<TurRoutine, Error, TurRoutine>({
    mutationFn: (routine) => service.create(routine),
    onSuccess: () => invalidate(queryClient),
  });
}

export function useUpdateRoutine() {
  const queryClient = useQueryClient();
  return useMutation<TurRoutine, Error, TurRoutine>({
    mutationFn: (routine) => service.update(routine),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.routines.detail(saved.id), saved);
      }
      invalidate(queryClient);
    },
  });
}

export function useDeleteRoutine() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, TurRoutine>({
    mutationFn: (routine) => service.delete(routine),
    onSuccess: (_ok, routine) => {
      if (routine?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.routines.detail(routine.id) });
      }
      invalidate(queryClient);
    },
  });
}
