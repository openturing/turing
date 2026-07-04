import { useAdminRoles } from "@/api/queries/admin.queries";
import { ROUTES } from "@/app/routes.const";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { useGridAdapter } from "@/hooks/use-grid-adapter";
import { useTranslation } from "react-i18next";

/**
 * Bento admin roles list — T567. Roles are always JPA-backed (no Keycloak
 * discriminator), so the "New" tile is always shown. Rendered inside the
 * {@code BentoAdminPage} tab Outlet; row + "New" links point at the Bento
 * detail routes.
 */
export default function BentoAdminRolesListPage() {
  const { t } = useTranslation();
  const { data: roles, isError } = useAdminRoles();
  const error = isError ? t("admin.roles.loadFailed") : null;

  const gridItemList = useGridAdapter(roles, {
    name: "name",
    description: "description",
    url: (item) => `${ROUTES.BENTO_ADMIN_ROLES}/${item.id}`,
  });

  return (
    <LoadProvider checkIsNotUndefined={roles} error={error} tryAgainUrl={ROUTES.BENTO_ADMIN_ROLES}>
      {roles && (
        <GridList gridItemList={gridItemList}>
          <GridList.NewButton to={`${ROUTES.BENTO_ADMIN_ROLES}/new`} label="Role" />
        </GridList>
      )}
    </LoadProvider>
  );
}
