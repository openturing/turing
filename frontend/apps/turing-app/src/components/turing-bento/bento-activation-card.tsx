import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { IconKey, IconSparkles } from "@tabler/icons-react";

import { ROUTES } from "@/app/routes.const";
import { cn } from "@/lib/utils";
import { BentoVigletActivationDialog } from "./bento-viglet-activation-dialog";

export interface BentoActivationCardProps {
  /**
   * `full` (default) is the home card — headline, both paths side by side, and
   * the data-handling note. `compact` is the copy embedded in an AI surface's
   * no-model state, which already carries its own headline.
   */
  variant?: "full" | "compact";
  className?: string;
}

/**
 * T958 / §XIV.10.3 — the activation card: the one surface that answers "how do I
 * turn AI on?" for a fresh install.
 *
 * <p><b>Two paths, equally prominent, on purpose.</b> Turing's argument is
 * self-hosted, and a managed default that looks like the only way in reads as a
 * bait — which costs more trust than the onboarding credit buys. So "Activate
 * Viglet AI" and "Use my own API key" are the same size, the same weight, and
 * neither is styled as the recommendation.
 *
 * <p>The card also states plainly that the managed path sends prompts to
 * Viglet's endpoint — at the moment of the decision, not in a footer somewhere
 * the operator reads after the fact.
 */
export function BentoActivationCard({
  variant = "full",
  className,
}: Readonly<BentoActivationCardProps>) {
  const { t } = useTranslation();
  const [dialogOpen, setDialogOpen] = useState(false);
  const isFull = variant === "full";

  return (
    <>
      <div
        className={cn(
          "bento-tile bento-glass rounded-3xl p-6 md:p-8",
          isFull && "mb-8 md:mb-10",
          className,
        )}
      >
        {isFull && (
          <div className="mb-6">
            <h2 className="text-xl font-semibold tracking-tight">
              {t("bento.activate.title", { defaultValue: "Turn on AI" })}
            </h2>
            <p className="mt-1 max-w-2xl text-sm text-muted-foreground">
              {t("bento.activate.subtitle", {
                defaultValue:
                  "Turing needs a language model before chat, retrieval, agents and semantic navigation can answer anything. Pick either way in — both work the same afterwards.",
              })}
            </p>
          </div>
        )}

        {/*
         * A two-column grid rather than a primary/secondary pair: the managed
         * path and BYOK get identical framing, identical size, identical
         * affordance. Whichever the operator picks, the product works the same.
         */}
        <div className="grid gap-4 md:grid-cols-2">
          <button
            type="button"
            onClick={() => setDialogOpen(true)}
            className="bento-tile bento-tile-clickable flex flex-col items-start gap-3 rounded-2xl border border-border/60 bg-card/60 p-5 text-left backdrop-blur"
          >
            <span className="grid h-11 w-11 place-items-center rounded-2xl bg-linear-to-br from-blue-500 to-indigo-600 text-white shadow-md">
              <IconSparkles size={22} />
            </span>
            <span className="text-base font-semibold tracking-tight">
              {t("bento.activate.managed.title", { defaultValue: "Activate Viglet AI" })}
            </span>
            <span className="text-sm text-muted-foreground">
              {t("bento.activate.managed.body", {
                defaultValue:
                  "Pair this install in one click and start with a free managed model. No account, no card, no keys.",
              })}
            </span>
          </button>

          <Link
            to={`${ROUTES.BENTO_LLM_INSTANCE}/new`}
            className="bento-tile bento-tile-clickable flex flex-col items-start gap-3 rounded-2xl border border-border/60 bg-card/60 p-5 text-left backdrop-blur"
          >
            <span className="grid h-11 w-11 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
              <IconKey size={22} />
            </span>
            <span className="text-base font-semibold tracking-tight">
              {t("bento.activate.byok.title", { defaultValue: "Use my own API key" })}
            </span>
            <span className="text-sm text-muted-foreground">
              {t("bento.activate.byok.body", {
                defaultValue:
                  "Point Turing at OpenAI, Anthropic, Gemini, Bedrock, a local Ollama — anything you already run. Free and fully featured.",
              })}
            </span>
          </Link>
        </div>

        {/*
         * The disclosure belongs beside the choice, not in a footer: an operator
         * deciding between self-hosted and managed is deciding where their
         * prompts go, and that is the fact they need at this exact moment.
         */}
        <p className="mt-4 text-xs text-muted-foreground">
          {t("bento.activate.disclosure", {
            defaultValue:
              "Viglet AI runs on Viglet's endpoint: prompts and retrieved content leave this install. Your own API key keeps that traffic between you and your provider.",
          })}
        </p>
      </div>

      <BentoVigletActivationDialog open={dialogOpen} onOpenChange={setDialogOpen} />
    </>
  );
}
