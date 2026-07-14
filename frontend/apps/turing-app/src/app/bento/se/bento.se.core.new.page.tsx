import { ROUTES } from "@/app/routes.const";
import SEInstanceCoreNewPage from "@/app/console/se/cores/se.instance.core.new.page";
import { SectionCardChromeProvider } from "@/components/ui/section-card";

/**
 * Bento "New Core" — reuses the console {@link SEInstanceCoreNewPage} form with
 * `chrome="bento"`, so it renders the {@link import("@/components/bento").BentoFormHero}
 * (hero → sticky-save-bar morph + `←` breadcrumb) and the frosted body via the
 * {@link SectionCardChromeProvider}, and returns save/cancel to the bento cores list.
 */
export default function BentoSECoreNewPage() {
  return (
    <SectionCardChromeProvider chrome="bento">
      <SEInstanceCoreNewPage chrome="bento" baseRoute={ROUTES.BENTO_SE_INSTANCE} />
    </SectionCardChromeProvider>
  );
}
