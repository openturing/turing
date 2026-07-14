import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { AssetBrowser } from "@/components/asset/asset-browser";
import { IconFolder } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";

/**
 * Bento asset browser — T561. The asset/file manager is an app-like surface
 * (grid/table + preview + upload), not a hero/save-bar CRUD, so it "sits inside
 * the shell" per §XXXI.7: a `BentoHero` over the shared {@link AssetBrowser}
 * (extracted from the console page — folder nav is component state, not routes,
 * so it is route-shell agnostic). Reachable via T549 nav, not the home mosaic.
 */
export default function BentoAssetPage() {
  const { t } = useTranslation();
  return (
    <>
      <BentoHero
        backTo={ROUTES.BENTO_AREA_MANAGEMENT}
        backLabel={t("home.sections.management.label")}
        leading={
          <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-amber-500 to-orange-600 text-white shadow-md">
            <IconFolder size={24} />
          </span>
        }
        title={t("home.features.assets.title")}
        subtitle={t("home.features.assets.description")}
      />
      <AssetBrowser tryAgainUrl={ROUTES.BENTO_ASSET} />
    </>
  );
}
