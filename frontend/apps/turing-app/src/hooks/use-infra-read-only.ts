import { useFeatures } from "@/api/queries/features.queries";

/**
 * T372 — read-only gating for the shared GLOBAL BYO-infra pool.
 *
 * When multi-tenancy is switched on, BYO-infra entities (Search Engine, LLM,
 * Embedding Model, Embedding Store, Integration, MCP Server) whose
 * `tenantId === null` belong to the platform-provided GLOBAL pool. Those rows
 * are seeded/owned by the platform and must be read-only in the admin UI for
 * every caller except a platform admin (`ROLE_PLATFORM_ADMIN`).
 *
 * On tenancy-off installs (`tenancyEnabled === false`) — and while the features
 * query is still loading — nothing is global and nothing is read-only, so the
 * admin UI behaves exactly as before.
 *
 * The backend independently returns 403 on writes to a read-only GLOBAL
 * instance; this hook is the UX / defense-in-depth layer.
 *
 * @param tenantId the entity's `tenantId` (a new/create entity has none, so it
 *   is never global).
 * @returns `isGlobal` — the row belongs to the GLOBAL pool (badge regardless of
 *   role); `readOnly` — the current caller may not edit/delete it.
 */
export function useInfraReadOnly(
  tenantId: string | null | undefined,
): { isGlobal: boolean; readOnly: boolean } {
  const { data: features } = useFeatures();

  const tenancyEnabled = features?.tenancyEnabled === true;
  const platformAdmin = features?.platformAdmin === true;

  const isGlobal = tenancyEnabled && tenantId == null;
  const readOnly = isGlobal && !platformAdmin;

  return { isGlobal, readOnly };
}
