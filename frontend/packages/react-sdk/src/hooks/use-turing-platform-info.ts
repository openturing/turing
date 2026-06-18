import { useEffect, useState } from "react";
import {
  fetchDiscovery,
  fetchFeatures,
  fetchLlmVendors,
  fetchSystemLocales,
  type TurDiscoveryInfo,
  type TurFeaturesInfo,
  type TurSystemLocale,
} from "../core/api";
import type { TurLlmVendor } from "../core/types";

export interface UseTuringPlatformInfoOptions {
  /** Disables the initial fetch when {@code false}. Defaults to {@code true}. */
  readonly enabled?: boolean;
  /** Skip the {@code /llm/vendor} fetch (secured on some deployments). Default {@code true}. */
  readonly includeVendors?: boolean;
  /** Skip the {@code /locale} fetch. Default {@code true}. */
  readonly includeLocales?: boolean;
}

export interface UseTuringPlatformInfoReturn {
  /** Platform identity / auth topology ({@code GET /discovery}). */
  readonly discovery: TurDiscoveryInfo | null;
  /** Enabled platform capabilities ({@code GET /features}). */
  readonly features: TurFeaturesInfo | null;
  /** Supported system locales ({@code GET /locale}); empty when not requested. */
  readonly locales: TurSystemLocale[];
  /** Configured LLM vendors ({@code GET /llm/vendor}); empty when not requested. */
  readonly vendors: TurLlmVendor[];
  /** {@code true} once the initial fetch settles (success or failure). */
  readonly loaded: boolean;
  /** Aggregated error message from the discovery/features fetch, or {@code null}. */
  readonly error: string | null;
}

/**
 * T405 — discovery helpers so a generic embed can self-configure: which
 * platform features are on, which locales/vendors exist, and the auth topology.
 * Bundles {@code /discovery} + {@code /features} (always) with optional
 * {@code /locale} + {@code /llm/vendor} reads.
 *
 * @example
 * ```tsx
 * const { features } = useTuringPlatformInfo();
 * if (features?.ragEnabled) renderChatTab();
 * ```
 *
 * @since 2026.3.4
 */
export function useTuringPlatformInfo(
  options: UseTuringPlatformInfoOptions = {},
): UseTuringPlatformInfoReturn {
  const { enabled = true, includeVendors = true, includeLocales = true } = options;

  const [discovery, setDiscovery] = useState<TurDiscoveryInfo | null>(null);
  const [features, setFeatures] = useState<TurFeaturesInfo | null>(null);
  const [locales, setLocales] = useState<TurSystemLocale[]>([]);
  const [vendors, setVendors] = useState<TurLlmVendor[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!enabled) return;
    let alive = true;
    setLoaded(false);
    setError(null);

    Promise.all([
      fetchDiscovery().then(
        (d) => {
          if (alive) setDiscovery(d);
        },
        (err: unknown) => {
          if (alive)
            setError(err instanceof Error ? err.message : "Failed to load discovery");
        },
      ),
      fetchFeatures().then(
        (f) => {
          if (alive) setFeatures(f);
        },
        (err: unknown) => {
          if (alive)
            setError(err instanceof Error ? err.message : "Failed to load features");
        },
      ),
      includeLocales
        ? fetchSystemLocales().then(
            (l) => {
              if (alive) setLocales(Array.isArray(l) ? l : []);
            },
            () => {
              /* locales are optional — ignore */
            },
          )
        : Promise.resolve(),
      includeVendors
        ? fetchLlmVendors().then(
            (v) => {
              if (alive) setVendors(Array.isArray(v) ? v : []);
            },
            () => {
              /* vendor listing may be secured — ignore */
            },
          )
        : Promise.resolve(),
    ]).finally(() => {
      if (alive) setLoaded(true);
    });

    return () => {
      alive = false;
    };
  }, [enabled, includeVendors, includeLocales]);

  return { discovery, features, locales, vendors, loaded, error };
}
