import { ROUTES } from "@/app/routes.const";
import { PersonaInstanceFormLayout } from "@/components/persona/persona.instance.form.layout";
import { useOutletContext } from "react-router-dom";
import type { BentoPersonaContext } from "./bento.persona.page";

/**
 * Bento persona section editor — Block AI / §XXXII.5 (T582). Wraps the shared
 * {@link PersonaInstanceFormLayout} (single `useForm` + pill tab-bar + save bar)
 * for the persona section routes, which now sit *below* the dashboard landing
 * rather than being the landing itself. Reads the persona from the loader's
 * {@link BentoPersonaContext} so both the dashboard and the editor share one
 * fetch. The layout renders its own `<Outlet>` for the active section.
 */
export default function BentoPersonaEditor() {
  const { persona, isNew } = useOutletContext<BentoPersonaContext>();
  return (
    <PersonaInstanceFormLayout
      value={persona}
      isNew={isNew}
      baseRoute={ROUTES.BENTO_PERSONA_INSTANCE}
      chrome="bento"
    />
  );
}
