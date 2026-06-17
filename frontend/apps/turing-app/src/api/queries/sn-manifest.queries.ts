import { useMutation, useQuery } from '@tanstack/react-query';
import type {
  TurSNManifestDeriveRequest,
  TurSNManifestResult,
  TurSNSiteManifest,
} from '@/models/sn/sn-manifest.model.ts';
import { TurSNManifestService } from '@/services/sn/sn.manifest.service';

const service = new TurSNManifestService();

/** T387 — whether an LLM-grounded derivation is available (false = heuristic-only fallback). */
export function useManifestDeriveAvailable(enabled = true) {
  return useQuery<boolean>({
    queryKey: ['sn-manifest', 'derive', 'available'],
    queryFn: () => service.deriveAvailable(),
    enabled,
  });
}

/** T387 — derive a draft manifest from a sample of a source's documents. */
export function useDeriveManifest() {
  return useMutation<TurSNSiteManifest, Error, TurSNManifestDeriveRequest>({
    mutationFn: (request) => service.derive(request),
  });
}

/** T382/T386 — converge the reviewed manifest into the live site (idempotent). */
export function useProvisionManifest() {
  return useMutation<TurSNManifestResult, Error, TurSNSiteManifest>({
    mutationFn: (manifest) => service.provision(manifest),
  });
}
