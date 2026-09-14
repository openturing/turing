import SNSiteCustomFacetItemPage from "@/app/console/sn/facet/sn.site.custom.facet.item.page";
import { ROUTES } from "@/app/routes.const";

/**
 * Bento SN custom-facet item editor — T576. Reuses the console page with the
 * bento base route (back/save stay in the shell), which makes the page render
 * the canonical `BentoFormHero` instead of the sidebar-coupled
 * `StickyPageHeader` (T738 — the hero and its sticky save-bar twin now come from
 * one component), and its `SectionCard` body as a frosted `BentoFormSection`.
 *
 * The `chrome="bento"` declaration that used to sit here went with the switch
 * itself (VDS146); bento had been the default since VDS145.
 */
export default function BentoSNCustomFacetItemPage() {
  return <SNSiteCustomFacetItemPage baseRoute={ROUTES.BENTO_SN_INSTANCE} />;
}
