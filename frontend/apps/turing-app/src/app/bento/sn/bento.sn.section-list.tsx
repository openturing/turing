import { ROUTES } from "@/app/routes.const";
import { BentoEntityTile, BentoListPage } from "@/components/bento";
import { type Icon as TablerIcon } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Shared scaffold for the GridList-style SN instance sub-section lists in the
 * bento shell (T576) — custom-sort, search-rule, result-ranking,
 * merge-providers, spotlight. Renders the canonical bento mosaic
 * ({@link BentoListPage} + {@link BentoEntityTile}) with a back-link eyebrow to
 * the SN instance, so items look like the other bento CRUD lists (not the
 * console card grid). Row + "New" tiles point at the bento sub-routes.
 */
export function BentoSnSectionList<T>({
  siteId,
  section,
  icon,
  title,
  subtitle,
  items,
  error,
  newLabel,
  emptyTitle,
  emptyDescription,
  itemKey,
  itemTitle,
  itemDescription,
}: Readonly<{
  siteId: string;
  /** Sub-route segment, e.g. "custom-sort". */
  section: string;
  icon: TablerIcon;
  title: string;
  subtitle: string;
  items: T[] | undefined;
  error: string | null;
  newLabel: string;
  emptyTitle: string;
  emptyDescription: string;
  /** Stable id used for the tile key + detail-route segment. */
  itemKey: (item: T) => string;
  itemTitle: (item: T) => string;
  itemDescription: (item: T) => string | null | undefined;
}>) {
  const { t } = useTranslation();
  const instanceRoute = `${ROUTES.BENTO_SN_INSTANCE}/${siteId}`;
  const sectionRoute = `${instanceRoute}/${section}`;

  return (
    <BentoListPage
      items={items}
      error={error}
      tryAgainUrl={sectionRoute}
      backTo={instanceRoute}
      backLabel={t("sn.title")}
      heroIcon={icon}
      tone="emerald"
      title={title}
      subtitle={subtitle}
      newRoute={`${sectionRoute}/new`}
      newLabel={newLabel}
      itemKey={itemKey}
      emptyTitle={emptyTitle}
      emptyDescription={emptyDescription}
      renderTile={(item, emphasis) => (
        <BentoEntityTile
          to={`${sectionRoute}/${itemKey(item)}`}
          emphasis={emphasis}
          defaultIcon={icon}
          tone="emerald"
          title={itemTitle(item)}
          description={itemDescription(item)}
        />
      )}
    />
  );
}
