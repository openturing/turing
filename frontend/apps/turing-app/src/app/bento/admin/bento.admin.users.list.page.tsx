import { useAdminUsers } from "@/api/queries/admin.queries";
import { useAuthDiscovery } from "@/api/queries/auth.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoTileGrid } from "@/components/bento";
import { IconUsers } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento admin users list — T567. Rendered inside the {@code BentoAdminPage}
 * hero + pill tab-bar Outlet, so it drops the console `SubPageHeader`/breadcrumb
 * and renders the frosted bento mosaic ({@link BentoTileGrid} + tiles) instead
 * of the console `GridList` card grid. Keeps the Keycloak-vs-JPA `useAdminUsers`
 * dependent query; the "New" tile is hidden for Keycloak (read-only) directories.
 * Row + "New" links point at the Bento detail routes.
 */
export default function BentoAdminUsersListPage() {
  const { t } = useTranslation();
  const { data: discovery, isError: discoveryError } = useAuthDiscovery();
  const keycloak: boolean | null =
    discovery !== undefined ? !!discovery.keycloak : discoveryError ? false : null;
  const { data: users, isError: usersError } = useAdminUsers(keycloak);
  const error = usersError ? t("admin.users.loadFailed") : null;

  return (
    <BentoTileGrid
      items={users}
      error={error}
      tryAgainUrl={ROUTES.BENTO_ADMIN_USERS}
      tone="slate"
      hideNew={!!keycloak}
      newRoute={`${ROUTES.BENTO_ADMIN_USERS}/new`}
      newLabel={t("admin.users.newUser")}
      newSubtitle={t("admin.users.createDescription")}
      itemKey={(u) => u.username}
      renderTile={(u) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_ADMIN_USERS}/${u.username}`}
          defaultIcon={IconUsers}
          tone="slate"
          title={u.username}
          description={u.description}
        />
      )}
      emptyTitle={t("admin.users.title")}
      emptyDescription={t("admin.users.description")}
    />
  );
}
