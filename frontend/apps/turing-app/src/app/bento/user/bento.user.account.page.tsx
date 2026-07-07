import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { IconUserCog, IconUserCircle, IconWorld } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet } from "react-router-dom";

/**
 * Bento "Account" layout — T568. The user-account area (profile · preferences)
 * sits inside the shell per §XXXI.9 as a {@link BentoHero} + a frosted pill
 * tab-bar over the reused console profile/preferences content components
 * (rendered through the {@code <Outlet />} with `header={null}` so their
 * sidebar-coupled `SubPageHeader` is omitted).
 */
export default function BentoUserAccountPage() {
  const { t } = useTranslation();

  const tabs = [
    { to: `${ROUTES.BENTO_USER_ACCOUNT}/profile`, icon: IconUserCircle, label: t("account.nav.profile") },
    { to: `${ROUTES.BENTO_USER_ACCOUNT}/preferences`, icon: IconWorld, label: t("account.nav.preferences") },
  ];

  return (
    <>
      <BentoHero
        eyebrow={t("account.feature")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-blue-500 to-indigo-600 text-white shadow-md">
            <IconUserCog size={24} />
          </span>
        }
        title={t("account.title")}
        subtitle={t("account.profile.description")}
      />

      <nav className="mb-6 flex flex-wrap gap-2">
        {tabs.map((tab) => {
          const TabIcon = tab.icon;
          return (
            <NavLink
              key={tab.to}
              to={tab.to}
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
