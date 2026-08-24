import { usePersona } from "@/api/queries/persona.queries";
import { ROUTES } from "@/app/routes.const";
import { LoadProvider } from "@/components/loading-provider";
import type { TurPersona } from "@/models/persona/persona.model.ts";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Outlet, useLocation, useParams } from "react-router-dom";

/**
 * Context threaded from the Bento persona loader to its child routes (the
 * dashboard landing and the section editor). A single fetch feeds both so the
 * dashboard's inline edits and the editor's form operate on the same entity.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export interface BentoPersonaContext {
  persona: TurPersona;
  isNew: boolean;
  /** Replace the loaded persona (used by the dashboard's inline-edit persist). */
  setPersona: (persona: TurPersona) => void;
}

/**
 * Bento Persona detail loader — T582. Fetches the persona once and exposes it
 * to child routes via the router `<Outlet context>`:
 *
 * - the index route renders the AI-agent-style **dashboard** (BentoHero +
 *   BentoTile mosaic + Persona Launchpad), superseding T556's "reuse the
 *   pill-tab form as the landing";
 * - the section routes render the shared-form editor (pill tab-bar + single
 *   `useForm` + save bar) unchanged, still reachable from the dashboard tiles.
 */
export default function BentoPersonaPage() {
  const { id } = useParams() as { id: string };
  const location = useLocation();
  const draft = (location.state as { draft?: TurPersona } | null)?.draft;
  const { t } = useTranslation();
  const [persona, setPersona] = useState<TurPersona>();
  // T1017 — derived, not stored. `isNew` was state that an effect wrote to
  // `id === "new"`, which is the same value the router already has: holding it
  // meant a route change from an existing record to "new" rendered one pass
  // with the previous answer before the effect corrected it.
  const isNew = id === "new";
  // T1018 — the saved persona comes from the query cache; this page keeps an
  // editable copy because the section sub-routes write to it through the Outlet
  // context. The hook is disabled on the `new` route, which has nothing to read.
  const { data: fetched, isError } = usePersona(isNew ? undefined : id);
  const error = isError
    ? t("common.connectionError", { resource: t("persona.title") })
    : null;

  // Seeding, adjusted during render rather than in an effect. `synced` is the
  // whole of what the old `seededNewRef` did: the section sub-routes
  // (/new/general, /new/system-instruction, …) are separate URLs that carry no
  // router state, so an audio-derived draft lives only in the state of the FIRST
  // /new navigation. `id` stays "new" across all of them, so this seeds exactly
  // once per "new" session and re-seeds when the route moves to another record.
  const [synced, setSynced] = useState<{ id: string; record?: TurPersona } | null>(null);
  if (isNew) {
    if (synced?.id !== id) {
      setSynced({ id });
      const seeded = draft ? { ...draft } : ({ name: "", verbosity: 3, enabled: 1 } as TurPersona);
      if (draft) delete (seeded as Partial<TurPersona>).id;
      setPersona(seeded as TurPersona);
    }
  } else if (fetched && (synced?.id !== id || synced.record !== fetched)) {
    setSynced({ id, record: fetched });
    setPersona(fetched);
  }

  return (
    <LoadProvider checkIsNotUndefined={persona} error={error} tryAgainUrl={`${ROUTES.BENTO_PERSONA_INSTANCE}/${id}`}>
      {persona && (
        <Outlet context={{ persona, isNew, setPersona } satisfies BentoPersonaContext} />
      )}
    </LoadProvider>
  );
}
