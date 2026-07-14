import { useAdminUsers } from "@/api/queries/admin.queries";
import { useAuthDiscovery } from "@/api/queries/auth.queries";
import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoHero, BentoTileGrid } from "@/components/bento";
import { IconUsers } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento admin users list — a standalone Management card (the Administration hub
 * was retired; users/groups/roles are now top-level cards). Owns its own
 * {@link BentoHero} (back-link to the Management area) above the frosted bento
 * mosaic ({@link BentoTileGrid} + tiles). Keeps the Keycloak-vs-JPA
 * `useAdminUsers` dependent query; the "New" tile is hidden for Keycloak
 * (read-only) directories. Row + "New" links point at the Bento detail routes.
 */
export default function BentoAdminUsersListPage() {
  const { t } = useTranslation();
  const { data: discovery, isError: discoveryError } = useAuthDiscovery();
  const keycloak: boolean | null =
    discovery !== undefined ? !!discovery.keycloak : discoveryError ? false : null;
  const { data: users, isError: usersError } = useAdminUsers(keycloak);
  const error = usersError ? t("admin.users.loadFailed") : null;

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_AREA_MANAGEMENT}
        backLabel={t("home.sections.management.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-slate-600 to-slate-700 text-white shadow-md">
            <IconUsers size={24} />
          </span>
        }
        title={t("admin.users.title")}
        subtitle={t("admin.users.description")}
      />
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
    </>
  );
}
