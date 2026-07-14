import { ROUTES } from "@/app/routes.const";
import { AdminRoleForm } from "@/components/admin/admin.role.form";
import { LoadProvider } from "@/components/loading-provider";
import { SectionCardChromeProvider } from "@/components/ui/section-card";
import type { TurRole } from "@/models/auth/role";
import { TurRoleService } from "@/services/auth/role.service";
import { IconUserShield } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

const turRoleService = new TurRoleService();

/**
 * Bento admin role detail — T567. Roles are always JPA-backed (no Keycloak
 * branch); reuses the shared `AdminRoleForm` with the Bento list route so
 * save/cancel stay inside the Bento shell.
 */
export default function BentoAdminRolePage() {
  const { t } = useTranslation();
  const { roleId } = useParams() as { roleId: string };
  const [role, setRole] = useState<TurRole>();
  const [isNew, setIsNew] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (roleId === "new") {
      setRole({ id: "", name: "", description: "" } as TurRole);
      setIsNew(true);
    } else {
      turRoleService
        .get(roleId)
        .then(setRole)
        .catch(() => setError(t("admin.roles.loadOneFailed")));
      setIsNew(false);
    }
  }, [roleId, t]);

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
                <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-slate-600 to-slate-700 text-white shadow-md">
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
