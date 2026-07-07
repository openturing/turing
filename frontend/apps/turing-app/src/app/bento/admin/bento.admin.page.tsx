import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import {
  IconCode,
  IconSettingsCog,
  IconShieldCog,
  IconUsers,
  IconUsersGroup,
  IconUserShield,
} from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet } from "react-router-dom";

/**
 * Bento "Administration" layout — T567. Users / groups / roles / settings /
 * tokens are the admin-settings area; per §XXXI.9 they sit inside the shell as
 * a {@link BentoHero} + a frosted pill tab-bar. The users/groups/roles tabs
 * render their reused list content through the {@code <Outlet />}; settings and
 * tokens already have their own migrated Bento surfaces (T566/T553), so their
 * pills link out to them. The Keycloak-vs-JPA dependent-query pattern is
 * preserved inside each list/detail page.
 */
export default function BentoAdminPage() {
  const { t } = useTranslation();

  const tabs = [
    { to: ROUTES.BENTO_ADMIN_USERS, icon: IconUsers, label: t("admin.users.title"), end: false },
    { to: ROUTES.BENTO_ADMIN_GROUPS, icon: IconUsersGroup, label: t("admin.groups.title"), end: false },
    { to: ROUTES.BENTO_ADMIN_ROLES, icon: IconUserShield, label: t("admin.roles.title"), end: false },
    { to: ROUTES.BENTO_GLOBAL_SETTINGS, icon: IconSettingsCog, label: t("globalSettings.breadcrumb", { defaultValue: "Settings" }), end: false },
    { to: ROUTES.BENTO_TOKEN_INSTANCE, icon: IconCode, label: t("apiToken.title"), end: false },
  ];

  return (
    <>
      <BentoHero
        eyebrow={t("home.sections.management.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-slate-500 to-slate-700 text-white shadow-md">
            <IconShieldCog size={24} />
          </span>
        }
        title={t("home.features.administration.title")}
        subtitle={t("home.features.administration.description")}
      />

      <nav className="mb-6 flex flex-wrap gap-2">
        {tabs.map((tab) => {
          const TabIcon = tab.icon;
          return (
            <NavLink
              key={tab.to}
              to={tab.to}
              end={tab.end}
              className={({ isActive }) =>
                `bento-tile bento-tile-clickable inline-flex items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-sm backdrop-blur transition-colors ${
                  isActive
                    ? "border-primary/40 bg-primary text-primary-foreground"
                    : "border-border/60 bg-card/60 text-muted-foreground hover:text-foreground"
                }`
              }
            >
              <TabIcon size={16} />
              {tab.label}
            </NavLink>
          );
        })}
      </nav>

      <Outlet />
    </>
  );
}
