import { useQuery } from '@tanstack/react-query';
import { type FeaturesResponse, TurFeaturesService } from '@/services/system/features.service';
import { queryKeys } from './keys';

const service = new TurFeaturesService();

/**
 * Cached feature flags. Bumped to a longer staleTime than the global default
 * because feature toggles change rarely and most UI relies on them in
 * critical paths (sidebar visibility, action availability).
 */
export function useFeatures() {
  return useQuery<FeaturesResponse>({
    queryKey: queryKeys.features.current(),
    queryFn: () => service.getFeatures(),
    staleTime: 5 * 60_000,
  });
}
