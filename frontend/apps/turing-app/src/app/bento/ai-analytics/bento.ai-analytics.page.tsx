import { ROUTES } from "@/app/routes.const";
import { useAiAnalyticsNav } from "@/app/console/ai-analytics/use-ai-analytics-nav";
import { BentoHero } from "@/components/bento";
import { IconReportAnalytics } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { NavLink, Outlet } from "react-router-dom";

/**
 * Bento "AI Analytics & Costs" layout — T564. The observability suite
 * (cost-governance · chat-analytics · parked-conversations · capability-matrix)
 * is read-mostly and bespoke, so per §XXXI.8 it "sits inside the shell": a
 * {@link BentoHero} + a frosted pill tab-bar over the reused sub-page content
 * components (rendered through the {@code <Outlet />}). Visibility rules are
 * shared with the console via {@link useAiAnalyticsNav} so the two never
 * disagree on which sub-pages an LLM-less / non-admin user sees.
 */
export default function BentoAiAnalyticsPage() {
  const { t } = useTranslation();
  const { items } = useAiAnalyticsNav();

  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_AREA_GENERATIVE_AI}
        backLabel={t("home.sections.generativeAi.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-indigo-500 to-blue-600 text-white shadow-md">
            <IconReportAnalytics size={24} />
          </span>
        }
        title={t("home.features.aiAnalytics.title")}
        subtitle={t("home.features.aiAnalytics.description")}
      />

      {items.length > 1 && (
        <nav className="mb-6 flex flex-wrap gap-2">
          {items.map((item) => {
            const ItemIcon = item.icon;
            return (
              <NavLink
                key={item.url}
                to={`${ROUTES.BENTO_AI_ANALYTICS}${item.url}`}
                className={({ isActive }) =>
                  `bento-tile bento-tile-clickable inline-flex items-center gap-1.5 rounded-full border px-3.5 py-1.5 text-sm backdrop-blur transition-colors ${
                    isActive
                      ? "border-primary/40 bg-primary text-primary-foreground"
                      : "border-border/60 bg-card/60 text-muted-foreground hover:text-foreground"
                  }`
                }
              >
                <ItemIcon size={16} />
                {item.title}
              </NavLink>
            );
          })}
        </nav>
      )}

      <Outlet />
    </>
  );
}
