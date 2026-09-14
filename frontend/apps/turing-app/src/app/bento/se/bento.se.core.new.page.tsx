import { ROUTES } from "@/app/routes.const";
import SEInstanceCoreNewPage from "@/app/console/se/cores/se.instance.core.new.page";

/**
 * Bento "New Core" — reuses the console {@link SEInstanceCoreNewPage} form, so it
 * renders the {@link import("@/components/bento").BentoFormHero} (hero →
 * sticky-save-bar morph + `←` breadcrumb) and the frosted body, and returns
 * save/cancel to the bento cores list.
 *
 * It used to declare `chrome="bento"` above the form. The design system removed
 * that switch once every consumer had cut over (VDS146), and bento had been the
 * default since VDS145 — so the declaration was naming the only answer.
 */
export default function BentoSECoreNewPage() {
  return <SEInstanceCoreNewPage baseRoute={ROUTES.BENTO_SE_INSTANCE} />;
}
