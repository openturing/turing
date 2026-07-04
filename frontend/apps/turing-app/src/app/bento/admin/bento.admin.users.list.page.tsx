import { useAdminUsers } from "@/api/queries/admin.queries";
import { useAuthDiscovery } from "@/api/queries/auth.queries";
import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { useTranslation } from "react-i18next";

/**
 * Bento admin users list — T567. Rendered inside the {@code BentoAdminPage}
 * tab-bar Outlet, so it drops the console `SubPageHeader`/breadcrumb (which need
 * the sidebar/breadcrumb providers absent in the Bento shell) and reuses the
 * shared `GridList` + the Keycloak-vs-JPA `useAdminUsers` dependent query. Row +
 * "New" links point at the Bento detail routes.
 */
export default function BentoAdminUsersListPage() {
  const { t } = useTranslation();
  const { data: discovery, isError: discoveryError } = useAuthDiscovery();
  const keycloak: boolean | null =
    discovery !== undefined ? !!discovery.keycloak : discoveryError ? false : null;
  const { data: users, isError: usersError } = useAdminUsers(keycloak);
  const error = usersError ? t("admin.users.loadFailed") : null;

  const gridItemList = useGridAdapter(users, {
    id: "username",
    name: "username",
    description: "description",
    url: (item) => `${ROUTES.BENTO_ADMIN_USERS}/${item.username}`,
  });

  return (
    <LoadProvider checkIsNotUndefined={users} error={error} tryAgainUrl={ROUTES.BENTO_ADMIN_USERS}>
      {users && (
        <GridList gridItemList={gridItemList}>
          {!keycloak && <GridList.NewButton to={`${ROUTES.BENTO_ADMIN_USERS}/new`} label="User" />}
        </GridList>
      )}
    </LoadProvider>
  );
}
