import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { BlankSlate } from "@/components/blank-slate";
import { GridList } from "@/components/grid.list";
import { LoadProvider } from "@/components/loading-provider";
import { GradientButtonLink } from "@viglet/viglet-design-system/router";
import type { TurGridItem } from "@/models/ui/grid-item";
import { IconPlus, type Icon as TablerIcon } from "@tabler/icons-react";
import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";

/**
 * Shared scaffold for the GridList-based SN instance sub-section lists in the
 * bento shell (T576) — custom-sort, search-rule, result-ranking,
 * merge-providers, spotlight. Mirrors the T558 field-list style: a
 * {@link BentoHero} with a back-link eyebrow to the SN instance + a "New"
 * action, over the reused console `GridList` (or a `BlankSlate` when empty).
 * The row + "New" links point at the bento sub-routes.
 */
export function BentoSnSectionList({
  siteId,
  section,
  icon: Icon,
  title,
  subtitle,
  items,
  gridItemList,
  error,
  newLabel,
  blankTitle,
  blankDescription,
  blankButton,
  extraTrailing,
}: Readonly<{
  siteId: string;
  /** Sub-route segment, e.g. "custom-sort". */
  section: string;
  icon: TablerIcon;
  title: string;
  subtitle: string;
  items: unknown[] | undefined;
  gridItemList: TurGridItem[];
  error: string | null;
  newLabel: string;
  blankTitle: string;
  blankDescription: string;
  blankButton: string;
  extraTrailing?: ReactNode;
}>) {
  const { t } = useTranslation();
  const instanceRoute = `${ROUTES.BENTO_SN_INSTANCE}/${siteId}`;
  const sectionRoute = `${instanceRoute}/${section}`;

  return (
    <LoadProvider checkIsNotUndefined={items} error={error} tryAgainUrl={sectionRoute}>
      <BentoHero
        eyebrow={<Link to={instanceRoute} className="hover:text-foreground">{t("sn.title")}</Link>}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
            <Icon size={24} />
          </span>
        }
        title={title}
        subtitle={subtitle}
        trailing={
          <>
            <GradientButtonLink to={`${sectionRoute}/new`} size="sm">
              <IconPlus className="size-4" />
              {newLabel}
            </GradientButtonLink>
            {extraTrailing}
          </>
        }
      />
      {items && items.length > 0 ? (
        <GridList gridItemList={gridItemList} />
      ) : (
        <BlankSlate icon={Icon} title={blankTitle} description={blankDescription} buttonText={blankButton} urlNew={`${sectionRoute}/new`} />
      )}
    </LoadProvider>
  );
}
