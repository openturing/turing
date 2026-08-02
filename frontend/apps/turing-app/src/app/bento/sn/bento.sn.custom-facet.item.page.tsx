import SNSiteCustomFacetItemPage from "@/app/console/sn/facet/sn.site.custom.facet.item.page";
import { ROUTES } from "@/app/routes.const";
import { SectionCardChromeProvider } from "@/components/ui/section-card";

/**
 * Bento SN custom-facet item editor — T576. Reuses the console page with the
 * bento base route (back/save stay in the shell) and `chrome="bento"`, which
 * makes the page render the canonical `BentoFormHero` instead of the
 * sidebar-coupled `StickyPageHeader` (T738 — the hero and its sticky save-bar
 * twin now come from one component). The `bento`
 * {@link SectionCardChromeProvider} renders its `SectionCard` body as a
 * frosted `BentoFormSection`.
 */
export default function BentoSNCustomFacetItemPage() {
  return (
    <SectionCardChromeProvider chrome="bento">
      <SNSiteCustomFacetItemPage baseRoute={ROUTES.BENTO_SN_INSTANCE} chrome="bento" />
    </SectionCardChromeProvider>
  );
}
