import { ROUTES } from "@/app/routes.const";
import SNOnboardingWizardPage from "@/app/console/sn/onboarding/sn.onboarding.wizard.page";

/**
 * Bento SN onboarding wizard — T559. The multi-step guided setup (sample →
 * derive schema → NL→facet eval → go live) is a self-contained stepper, so it
 * is reused verbatim with `baseRoute={ROUTES.BENTO_SN_INSTANCE}` — its
 * completion actions ("Configure site" / "Add fields") then land the user on
 * the Bento SN instance detail + field list instead of the console. Its own
 * `max-w-3xl` step layout sits directly inside the frosted bento main column.
 */
export default function BentoSNOnboardingPage() {
  return <SNOnboardingWizardPage baseRoute={ROUTES.BENTO_SN_INSTANCE} />;
}
