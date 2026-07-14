import { useAdminRoles } from "@/api/queries/admin.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoHero, BentoTileGrid } from "@/components/bento";
import { IconUserShield } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento admin roles list — a standalone Management card (the Administration hub
 * was retired). Roles are always JPA-backed (no Keycloak discriminator), so the
 * "New" tile is always shown. Owns its own {@link BentoHero} above the frosted
 * bento mosaic ({@link BentoTileGrid} + tiles); row + "New" links point at the
 * Bento detail routes.
 */
export default function BentoAdminRolesListPage() {
  const { t } = useTranslation();
  const { data: roles, isError } = useAdminRoles();
  const error = isError ? t("admin.roles.loadFailed") : null;

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_AREA_MANAGEMENT}
        backLabel={t("home.sections.management.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-slate-600 to-slate-700 text-white shadow-md">
            <IconUserShield size={24} />
          </span>
        }
        title={t("admin.roles.title")}
        subtitle={t("admin.roles.description")}
      />
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
    </>
  );
}
