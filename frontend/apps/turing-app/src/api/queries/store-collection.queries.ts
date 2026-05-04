import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  type TurStoreCollectionInfo,
  type TurStoreImportResult,
  TurStoreInstanceService,
} from '@/services/store/store.service';
import { queryKeys } from './keys';

const service = new TurStoreInstanceService();

/** Collections under a store instance. Disabled when storeId is missing. */
export function useStoreCollections(storeId: string | undefined) {
  return useQuery<TurStoreCollectionInfo[]>({
    queryKey: storeId
      ? queryKeys.storeCollections.listByInstance(storeId)
      : ['store-collections', 'pending'],
    queryFn: () => service.collections(storeId as string),
    enabled: Boolean(storeId),
  });
}

/**
 * Per-row distinct document count, fetched lazily after the listing renders.
 * The backend returns -1 in {@link TurStoreCollectionInfo.documentCount} when
 * computing it eagerly would be too expensive (Chroma scans chunk metadata);
 * pages call this hook for those rows so the table loads instantly and
 * counts fill in afterwards.
 *
 * @param enabled gates the request — pass {@code false} when the listing
 *                already came back with a real count.
 */
export function useStoreCollectionDocumentCount(
  storeId: string | undefined,
  collectionName: string | undefined,
  enabled: boolean,
) {
  return useQuery<number>({
    queryKey:
      storeId && collectionName
        ? queryKeys.storeCollections.documentCount(storeId, collectionName)
        : ['store-collections', 'document-count', 'pending'],
    queryFn: () => service.collectionDocumentCount(storeId as string, collectionName as string),
    enabled: enabled && Boolean(storeId) && Boolean(collectionName),
    staleTime: 60_000,
  });
}

function invalidateStoreCollections(
  queryClient: ReturnType<typeof useQueryClient>,
  storeId: string,
) {
  queryClient.invalidateQueries({
    queryKey: queryKeys.storeCollections.listByInstance(storeId),
  });
}

type CreateCollectionVars = { storeId: string; name: string };
type CollectionOpVars = { storeId: string; collectionName: string };
type ImportCollectionVars = {
  storeId: string;
  collectionName: string;
  file: File;
  onProgress?: (progress: number) => void;
  embeddingModelId?: string;
};

export function useCreateStoreCollection() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, CreateCollectionVars>({
    mutationFn: ({ storeId, name }) => service.createCollection(storeId, name),
    onSuccess: (_void, { storeId }) => invalidateStoreCollections(queryClient, storeId),
  });
}

/** Removes all documents from a collection but keeps the collection itself. */
export function useClearStoreCollection() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, CollectionOpVars>({
    mutationFn: ({ storeId, collectionName }) =>
      service.clearCollection(storeId, collectionName),
    onSuccess: (_void, { storeId }) => invalidateStoreCollections(queryClient, storeId),
  });
}

export function useDeleteStoreCollection() {
  const queryClient = useQueryClient();
  return useMutation<void, Error, CollectionOpVars>({
    mutationFn: ({ storeId, collectionName }) =>
      service.deleteCollection(storeId, collectionName),
    onSuccess: (_void, { storeId }) => invalidateStoreCollections(queryClient, storeId),
  });
}

/**
 * Re-imports a JSONL/zip dump into a collection. Counts (documents/chunks)
 * change after a successful import, so we invalidate the parent list.
 */
export function useImportStoreCollection() {
  const queryClient = useQueryClient();
  return useMutation<TurStoreImportResult, Error, ImportCollectionVars>({
    mutationFn: ({ storeId, collectionName, file, onProgress, embeddingModelId }) =>
      service.importCollection(storeId, collectionName, file, onProgress, embeddingModelId),
    onSuccess: (_result, { storeId }) => invalidateStoreCollections(queryClient, storeId),
  });
}
