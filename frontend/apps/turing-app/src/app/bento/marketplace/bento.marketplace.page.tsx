import { BentoHero } from "@/components/bento";
import { ROUTES } from "@/app/routes.const";
import MarketplaceListPage from "@/app/console/marketplace/marketplace.list.page";
import { IconBuildingStore } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento marketplace — T565. The storefront (sites + T96 flow recipes) is a
 * read-mostly grid, so per §XXXI.8 it sits under a {@link BentoHero} inside the
 * shell; the reused {@link MarketplaceListPage} keeps its filters, import
 * dialogs and recipe install flow unchanged.
 */
export default function BentoMarketplacePage() {
  const { t } = useTranslation();
  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_AREA_MANAGEMENT}
        backLabel={t("home.sections.management.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-blue-500 to-indigo-600 text-white shadow-md">
            <IconBuildingStore size={24} />
          </span>
        }
        title={t("home.features.marketplace.title")}
        subtitle={t("home.features.marketplace.description")}
      />
      <MarketplaceListPage />
    </>
  );
}
