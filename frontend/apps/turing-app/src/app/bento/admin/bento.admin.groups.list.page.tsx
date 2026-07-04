import { useAdminGroups } from "@/api/queries/admin.queries";
import { useAuthDiscovery } from "@/api/queries/auth.queries";
import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { useTranslation } from "react-i18next";

/**
 * Bento admin groups list — T567. Reuses the shared `GridList` + the
 * Keycloak-vs-JPA `useAdminGroups` dependent query inside the
 * {@code BentoAdminPage} tab Outlet; row + "New" links point at the Bento
 * detail routes.
 */
export default function BentoAdminGroupsListPage() {
  const { t } = useTranslation();
  const { data: discovery, isError: discoveryError } = useAuthDiscovery();
  const keycloak: boolean | null =
    discovery !== undefined ? !!discovery.keycloak : discoveryError ? false : null;
  const { data: groups, isError: groupsError } = useAdminGroups(keycloak);
  const error = groupsError ? t("admin.groups.loadFailed") : null;

  const gridItemList = useGridAdapter(groups, {
    name: "name",
    description: "description",
    url: (item) => `${ROUTES.BENTO_ADMIN_GROUPS}/${item.id}`,
  });

  return (
    <LoadProvider checkIsNotUndefined={groups} error={error} tryAgainUrl={ROUTES.BENTO_ADMIN_GROUPS}>
      {groups && (
        <GridList gridItemList={gridItemList}>
          {!keycloak && <GridList.NewButton to={`${ROUTES.BENTO_ADMIN_GROUPS}/new`} label="Group" />}
        </GridList>
      )}
    </LoadProvider>
  );
}
