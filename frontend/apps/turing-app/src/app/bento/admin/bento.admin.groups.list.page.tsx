import { useAdminGroups } from "@/api/queries/admin.queries";
import { useAuthDiscovery } from "@/api/queries/auth.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoTileGrid } from "@/components/bento";
import { IconUsersGroup } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento admin groups list — T567. Renders the frosted bento mosaic
 * ({@link BentoTileGrid} + tiles) inside the {@code BentoAdminPage} tab Outlet,
 * reusing the Keycloak-vs-JPA `useAdminGroups` dependent query; the "New" tile
 * is hidden for Keycloak (read-only) directories. Row + "New" links point at
 * the Bento detail routes.
 */
export default function BentoAdminGroupsListPage() {
  const { t } = useTranslation();
  const { data: discovery, isError: discoveryError } = useAuthDiscovery();
  const keycloak: boolean | null =
    discovery !== undefined ? !!discovery.keycloak : discoveryError ? false : null;
  const { data: groups, isError: groupsError } = useAdminGroups(keycloak);
  const error = groupsError ? t("admin.groups.loadFailed") : null;

  return (
    <BentoTileGrid
      items={groups}
      error={error}
      tryAgainUrl={ROUTES.BENTO_ADMIN_GROUPS}
      tone="slate"
      hideNew={!!keycloak}
      newRoute={`${ROUTES.BENTO_ADMIN_GROUPS}/new`}
      newLabel={t("admin.groups.newGroup")}
      newSubtitle={t("admin.groups.createDescription")}
      itemKey={(g) => g.id}
      renderTile={(g) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_ADMIN_GROUPS}/${g.id}`}
          defaultIcon={IconUsersGroup}
          tone="slate"
          title={g.name}
          description={g.description}
        />
      )}
      emptyTitle={t("admin.groups.title")}
      emptyDescription={t("admin.groups.description")}
    />
  );
}
