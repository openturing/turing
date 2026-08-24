import { useAdminRole } from "@/api/queries/admin.queries";
import { ROUTES } from "@/app/routes.const";
import { bentoChipClass } from "@/components/bento";
import { AdminRoleForm } from "@/components/admin/admin.role.form";
import { LoadProvider } from "@/components/loading-provider";
import { SectionCardChromeProvider } from "@/components/ui/section-card";
import type { TurRole } from "@/models/auth/role";
import { IconUserShield } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const NEW_ROLE = { id: "", name: "", description: "" } as TurRole;

/**
 * Bento admin role detail — T567. Roles are always JPA-backed (no Keycloak
 * branch); reuses the shared `AdminRoleForm` with the Bento list route so
 * save/cancel stay inside the Bento shell.
 */
export default function BentoAdminRolePage() {
  const { t } = useTranslation();
  const { roleId } = useParams() as { roleId: string };
  // T1017 — derived, not stored. `isNew` was state that an effect wrote to
  // `roleId === "new"`, which is the same value the router already has: holding it
  // meant a route change from an existing record to "new" rendered one pass
  // with the previous answer before the effect corrected it.
  const isNew = roleId === "new";

  // T1018 — the role comes from the query cache. The hook is disabled on the
  // `new` route, where the seed above is the whole record.
  const { data: fetched, isError } = useAdminRole(roleId);
  const role = isNew ? NEW_ROLE : fetched;
  const error = isError ? t("admin.roles.loadOneFailed") : null;

  return (
    <SectionCardChromeProvider chrome="bento">
      <LoadProvider checkIsNotUndefined={role} error={error} tryAgainUrl={`${ROUTES.BENTO_ADMIN_ROLES}/${roleId}`}>
        {role && (
          <AdminRoleForm
            value={role}
            isNew={isNew}
            listRoute={ROUTES.BENTO_ADMIN_ROLES}
            bentoHero={{
              backTo: ROUTES.BENTO_ADMIN_ROLES,
              backLabel: t("admin.roles.title"),
              leading: (
                <span className={`grid h-12 w-12 place-items-center rounded-2xl ${bentoChipClass('slate')} text-white shadow-md`}>
                  <IconUserShield size={24} />
                </span>
              ),
              title: isNew ? t("admin.roles.newRole") : (role.name || ""),
              subtitle: isNew ? t("admin.roles.createDescription") : t("admin.roles.editDescription"),
            }}
          />
        )}
      </LoadProvider>
    </SectionCardChromeProvider>
  );
}
