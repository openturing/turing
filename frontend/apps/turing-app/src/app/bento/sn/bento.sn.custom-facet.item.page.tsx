import SNSiteCustomFacetItemPage from "@/app/console/sn/facet/sn.site.custom.facet.item.page";
import { ROUTES } from "@/app/routes.const";
import { BentoHero } from "@/components/bento";
import { SectionCardChromeProvider } from "@/components/ui/section-card";
import { IconListDetails } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";

/**
 * Bento SN custom-facet item editor — T576. Reuses the console page with the
 * bento base route (back/save stay in the shell), a {@link BentoHero} header
 * (instead of the sidebar-coupled `StickyPageHeader`) and a `bento`
 * {@link SectionCardChromeProvider} so its `SectionCard` body renders as a
 * frosted {@link BentoFormSection}.
 */
export default function BentoSNCustomFacetItemPage() {
  const { id, customFacetId } = useParams() as { id: string; customFacetId: string };
  const { t } = useTranslation();
  const facetRoute = `${ROUTES.BENTO_SN_INSTANCE}/${id}/facet/custom/${customFacetId}`;

  return (
    <SectionCardChromeProvider chrome="bento">
      <SNSiteCustomFacetItemPage
        baseRoute={ROUTES.BENTO_SN_INSTANCE}
        header={
          <BentoHero
            backTo={facetRoute}
            backLabel={t("sn.facets.title")}
            leading={
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-amber-500 to-orange-500 text-white shadow-md">
                <IconListDetails size={24} />
              </span>
            }
            title={t("forms.snCustomFacet.facetItems")}
            subtitle={t("forms.snCustomFacet.itemDesc")}
          />
        }
      />
    </SectionCardChromeProvider>
  );
}
