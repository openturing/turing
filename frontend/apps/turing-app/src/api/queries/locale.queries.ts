import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';

import type { TurLocale } from '@/models/locale/locale.model';
import type { TurSNSiteLocale } from '@/models/sn/sn-site-locale.model';
import { TurLocaleService } from '@/services/locale/locale.service';
import { TurSNSiteLocaleService } from '@/services/sn/sn.site.locale.service';

import { queryKeys } from './keys';

const localeService = new TurLocaleService();
const snSiteLocaleService = new TurSNSiteLocaleService();

export function useGlobalLocales() {
  return useQuery<TurLocale[]>({
    queryKey: queryKeys.locales.list(),
    queryFn: () => localeService.query(),
  });
}

export function useSnSiteLocales(siteId: string | undefined) {
  return useQuery<TurSNSiteLocale[]>({
    queryKey: siteId ? queryKeys.snSiteLocales.listBySite(siteId) : ['sn-site-locales', 'pending'],
    queryFn: () => snSiteLocaleService.query(siteId as string),
    enabled: Boolean(siteId),
  });
}

/**
 * Locales configured for the SN site, intersected with the global TurLocale
 * catalogue so callers (e.g. label translations editors) only offer languages
 * the site actually serves. Falls back to the full catalogue while the site
 * locales are still loading or when the site has none configured.
 */
export function useSnSiteAvailableLocales(siteId: string | undefined) {
  const globalLocalesQuery = useGlobalLocales();
  const snSiteLocalesQuery = useSnSiteLocales(siteId);

  const data = useMemo<TurLocale[]>(() => {
    const all = globalLocalesQuery.data ?? [];
    const siteLocales = snSiteLocalesQuery.data ?? [];
    if (siteLocales.length === 0) {
      return all;
    }
    const allowed = new Set(siteLocales.map((locale) => locale.language));
    return all.filter((locale) => allowed.has(locale.initials));
  }, [globalLocalesQuery.data, snSiteLocalesQuery.data]);

  return {
    data,
    isLoading: globalLocalesQuery.isLoading || snSiteLocalesQuery.isLoading,
    isError: globalLocalesQuery.isError || snSiteLocalesQuery.isError,
  };
}
