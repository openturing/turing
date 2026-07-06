import { useAdminRoles } from "@/api/queries/admin.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoTileGrid } from "@/components/bento";
import { IconUserShield } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento admin roles list — T567. Roles are always JPA-backed (no Keycloak
 * discriminator), so the "New" tile is always shown. Renders the frosted bento
 * mosaic ({@link BentoTileGrid} + tiles) inside the {@code BentoAdminPage} tab
 * Outlet; row + "New" links point at the Bento detail routes.
 */
export default function BentoAdminRolesListPage() {
  const { t } = useTranslation();
  const { data: roles, isError } = useAdminRoles();
  const error = isError ? t("admin.roles.loadFailed") : null;

  return (
    <BentoTileGrid
      items={roles}
      error={error}
      tryAgainUrl={ROUTES.BENTO_ADMIN_ROLES}
      tone="slate"
      newRoute={`${ROUTES.BENTO_ADMIN_ROLES}/new`}
      newLabel={t("admin.roles.newRole")}
      newSubtitle={t("admin.roles.createDescription")}
      itemKey={(r) => r.id}
      renderTile={(r) => (
        <BentoEntityTile
          to={`${ROUTES.BENTO_ADMIN_ROLES}/${r.id}`}
          defaultIcon={IconUserShield}
          tone="slate"
          title={r.name}
          description={r.description}
        />
      )}
      emptyTitle={t("admin.roles.title")}
      emptyDescription={t("admin.roles.description")}
    />
  );
}
