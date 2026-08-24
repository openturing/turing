import { markBentoTourSeen } from "./bento-first-run-tour.storage";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { TurLogo } from "@/components/logo/tur-logo";
import {
  IconAdjustmentsHorizontal,
  IconCommand,
  IconLayoutGrid,
  IconSparkles,
  type Icon as TablerIcon,
} from "@tabler/icons-react";
import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

/**
 * localStorage key marking the first-run tour as seen. Bump the suffix to
 * re-show it to everyone after a big IA change.
 *
 * `v2` (T958) — the tour gained the "turn AI on" step. A tour that never
 * mentions the one thing blocking the product is a tour worth re-showing, so
 * existing installs see it once more.
 */

export interface BentoFirstRunTourProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

interface TourStep {
  icon: TablerIcon;
  tone: string; // tailwind gradient (from → to)
  titleKey: string;
  titleDef: string;
  bodyKey: string;
  bodyDef: string;
}

const STEPS: readonly TourStep[] = [
  /*
   * T958 — first, because it is first in practice. The other three steps are
   * all true and none of them is what stops a new operator: the install has no
   * LLM, so every AI surface the tour points at is dead. Teaching the furniture
   * before the blocker is the gap this step closes.
   */
  {
    icon: IconSparkles,
    tone: "from-blue-500 to-indigo-600",
    titleKey: "bento.tour.activate.title",
    titleDef: "Turn on AI first",
    bodyKey: "bento.tour.activate.body",
    bodyDef:
      "Turing ships without a language model, so chat, retrieval and agents stay quiet until you give it one. On the home screen, activate the free managed Viglet AI or paste your own API key — both work the same afterwards.",
  },
  {
    icon: IconLayoutGrid,
    tone: "from-blue-500 to-indigo-500",
    titleKey: "bento.tour.rail.title",
    titleDef: "One rail, three hubs",
    bodyKey: "bento.tour.rail.body",
    bodyDef:
      "The left rail keeps just Home and the three hubs — Generative AI, Enterprise Search, and Management. Everything else lives one tap inside its hub.",
  },
  {
    icon: IconCommand,
    tone: "from-indigo-500 to-fuchsia-500",
    titleKey: "bento.tour.palette.title",
    titleDef: "Jump anywhere with the palette",
    bodyKey: "bento.tour.palette.body",
    bodyDef:
      "Press ⌘K (Ctrl+K) from any screen to search and jump straight to any area. Press ? any time to see every shortcut.",
  },
  {
    icon: IconAdjustmentsHorizontal,
    tone: "from-emerald-500 to-teal-500",
    titleKey: "bento.tour.customize.title",
    titleDef: "Make it yours",
    bodyKey: "bento.tour.customize.body",
    bodyDef:
      "On any list, Customize layout lets you drag tiles to reorder them and pick their size. Your arrangement sticks across sessions.",
  },
];

/**
 * The first-run tour (T572) — a short, friendly welcome shown once on the very
 * first visit to the bento home. Stepped cards orient a new user to the one
 * thing blocking the product (turning AI on, T958), then to the hub-and-spoke
 * rail (T573), the ⌘K palette (T549), and the customizable layout (T575) —
 * the things that aren't obvious at a glance. Fully skippable,
 * keyboard-navigable, and remembered in localStorage so it never reappears; it
 * can be replayed from the user menu.
 *
 * Its entrance reuses `.bento-shell-header` (already under the reduced-motion
 * guard), so it introduces no unguarded animation.
 */
export function BentoFirstRunTour({ open, onOpenChange }: Readonly<BentoFirstRunTourProps>) {
  const { t } = useTranslation();
  const [step, setStep] = useState(0);

  const steps = useMemo(() => STEPS, []);
  const current = steps[Math.min(step, steps.length - 1)];
  const isLast = step >= steps.length - 1;

  function close(open: boolean) {
    if (!open) {
      markBentoTourSeen();
      setStep(0);
    }
    onOpenChange(open);
  }

  const Icon = current.icon;

  return (
    <Dialog open={open} onOpenChange={close}>
      <DialogContent showCloseButton={false} className="bento-shell-header max-w-md overflow-hidden p-0">
        <div className="flex flex-col gap-5 p-6">
          <div className="flex items-center gap-3">
            <TurLogo className="size-8" />
            <DialogTitle className="text-sm font-medium tracking-tight text-muted-foreground">
              {t("bento.tour.welcome", { defaultValue: "Welcome to Turing ES" })}
            </DialogTitle>
          </div>

          <span
            className={`grid h-14 w-14 place-items-center rounded-2xl bg-linear-to-br ${current.tone} text-white shadow-md`}
          >
            <Icon size={28} />
          </span>

          <div>
            <h2 className="text-xl font-semibold tracking-tight">
              {t(current.titleKey, { defaultValue: current.titleDef })}
            </h2>
            <DialogDescription className="mt-2 text-sm text-muted-foreground">
              {t(current.bodyKey, { defaultValue: current.bodyDef })}
            </DialogDescription>
          </div>

          {/* Step dots */}
          <div className="flex items-center gap-1.5" aria-hidden>
            {steps.map((s, i) => (
              <span
                key={s.titleKey}
                className={`h-1.5 rounded-full transition-all ${
                  i === step ? "w-6 bg-primary" : "w-1.5 bg-border"
                }`}
              />
            ))}
          </div>

          <div className="flex items-center justify-between gap-2">
            <Button variant="ghost" size="sm" onClick={() => close(false)}>
              {t("bento.tour.skip", { defaultValue: "Skip" })}
            </Button>
            <div className="flex items-center gap-2">
              {step > 0 && (
                <Button variant="outline" size="sm" onClick={() => setStep((s) => Math.max(0, s - 1))}>
                  {t("bento.tour.back", { defaultValue: "Back" })}
                </Button>
              )}
              {isLast ? (
                <Button size="sm" onClick={() => close(false)}>
                  {t("bento.tour.done", { defaultValue: "Get started" })}
                </Button>
              ) : (
                <Button size="sm" onClick={() => setStep((s) => Math.min(steps.length - 1, s + 1))}>
                  {t("bento.tour.next", { defaultValue: "Next" })}
                </Button>
              )}
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
