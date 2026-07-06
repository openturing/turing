import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { TurPersona } from '@/models/persona/persona.model.ts';
import { TurPersonaService } from '@/services/persona/persona.service';
import { queryKeys } from './keys';

const service = new TurPersonaService();

export function usePersonas() {
  return useQuery<TurPersona[]>({
    queryKey: queryKeys.personas.list(),
    queryFn: () => service.query(),
  });
}

export function usePersona(id: string | undefined) {
  return useQuery<TurPersona>({
    queryKey: id ? queryKeys.personas.detail(id) : ['personas', 'pending'],
    queryFn: () => service.get(id as string),
    enabled: Boolean(id),
  });
}

function invalidatePersonas(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.personas.all() });
}

export function useCreatePersona() {
  const queryClient = useQueryClient();
  return useMutation<TurPersona, Error, TurPersona>({
    mutationFn: (persona) => service.create(persona),
    onSuccess: () => invalidatePersonas(queryClient),
  });
}

export function useUpdatePersona() {
  const queryClient = useQueryClient();
  return useMutation<TurPersona, Error, TurPersona>({
    mutationFn: (persona) => service.update(persona),
    onSuccess: (saved) => {
      if (saved?.id) {
        queryClient.setQueryData(queryKeys.personas.detail(saved.id), saved);
      }
      invalidatePersonas(queryClient);
    },
  });
}

export function useDeletePersona() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, TurPersona>({
    mutationFn: (persona) => service.delete(persona),
    onSuccess: (_ok, persona) => {
      if (persona?.id) {
        queryClient.removeQueries({ queryKey: queryKeys.personas.detail(persona.id) });
      }
      invalidatePersonas(queryClient);
    },
  });
}
