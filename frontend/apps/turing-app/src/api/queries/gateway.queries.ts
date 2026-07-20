import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  TurGatewayCreatedKey,
  TurGatewayKeyUpsert,
  TurGatewayKeyUsageRow,
  TurGatewayKeyView,
} from "@/models/gateway/gateway.model.ts";
import { TurGatewayService } from "@/services/gateway/gateway.service";
import { queryKeys } from "./keys";

const service = new TurGatewayService();

export function useGatewayKeys() {
  return useQuery<TurGatewayKeyView[]>({
    queryKey: queryKeys.gateway.keys(),
    queryFn: () => service.listKeys(),
  });
}

export function useGatewayUsage() {
  return useQuery<TurGatewayKeyUsageRow[]>({
    queryKey: queryKeys.gateway.usage(),
    queryFn: () => service.usage(),
  });
}

function invalidateGateway(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: queryKeys.gateway.all() });
}

export function useCreateGatewayKey() {
  const queryClient = useQueryClient();
  return useMutation<TurGatewayCreatedKey, Error, TurGatewayKeyUpsert>({
    mutationFn: (request) => service.createKey(request),
    onSuccess: () => invalidateGateway(queryClient),
  });
}

export function useUpdateGatewayKey() {
  const queryClient = useQueryClient();
  return useMutation<TurGatewayKeyView, Error, { id: string; request: TurGatewayKeyUpsert }>({
    mutationFn: ({ id, request }) => service.updateKey(id, request),
    onSuccess: () => invalidateGateway(queryClient),
  });
}

export function useRotateGatewayKey() {
  const queryClient = useQueryClient();
  return useMutation<TurGatewayCreatedKey, Error, string>({
    mutationFn: (id) => service.rotateKey(id),
    onSuccess: () => invalidateGateway(queryClient),
  });
}

export function useDeleteGatewayKey() {
  const queryClient = useQueryClient();
  return useMutation<boolean, Error, string>({
    mutationFn: (id) => service.deleteKey(id),
    onSuccess: () => invalidateGateway(queryClient),
  });
}
