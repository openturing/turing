import SNSiteCustomFacetItemPage from "@/app/console/sn/facet/sn.site.custom.facet.item.page";
import { ROUTES } from "@/app/routes.const";

/** Bento SN custom-facet item editor — T576. Reuses the console page (its own
 *  sticky header) with the bento base route so back/save stay in the shell. */
export default function BentoSNCustomFacetItemPage() {
  return <SNSiteCustomFacetItemPage baseRoute={ROUTES.BENTO_SN_INSTANCE} />;
}
