import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";

import { BentoActivationCard } from "./bento-activation-card";

export interface BentoNoLlmStateProps {
  /** Page header to keep above the state, so the surface still reads as itself. */
  header?: ReactNode;
  /** Overrides the headline when a surface has a sharper way to say it. */
  title?: string;
  /** Overrides the one-line explanation of what this surface needs a model for. */
  description?: string;
}

/**
 * T958 / §XIV.10.3 — the one "this needs a model" state, shared by every AI
 * surface (chat, agents, skills, semantic-navigation answers).
 *
 * <p>Those surfaces used to fail late and opaquely — a provider error, or a
 * blank slate pointing at the console's LLM form — which reads as a broken build
 * rather than an unconfigured one. This says the plain thing instead, and offers
 * the same two paths the home activation card does by rendering that card
 * itself: an operator who lands here from a deep link should not have to
 * navigate home to act on what they were just told.
 */
export function BentoNoLlmState({ header, title, description }: Readonly<BentoNoLlmStateProps>) {
  const { t } = useTranslation();
  return (
    <>
      {header}
      <div className="mb-4">
        <h2 className="text-lg font-semibold tracking-tight">
          {title ?? t("bento.activate.noLlmTitle", { defaultValue: "This needs a language model" })}
        </h2>
        <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
          {description ??
            t("bento.activate.noLlmDescription", {
              defaultValue:
                "No model is configured yet, so there is nothing for this screen to ask. Choose either way in and come straight back.",
            })}
        </p>
      </div>
      <BentoActivationCard variant="compact" />
    </>
  );
}
