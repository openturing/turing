import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  IconAlertTriangle,
  IconCircleCheck,
  IconExternalLink,
  IconLoader2,
} from "@tabler/icons-react";

import { useRefreshAfterActivation } from "@/api/queries/viglet-ai.queries";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogTitle } from "@/components/ui/dialog";
import { GradientButton } from "@/components/ui/gradient-button";
import { TurLogo } from "@/components/logo/tur-logo";
import type { TurVigletPairingSession } from "@/models/genai/viglet-ai.model.ts";
import { TurVigletAiService } from "@/services/genai/viglet-ai.service";

const service = new TurVigletAiService();

/** The dialog's own phase — not the server's pairing state. */
type Phase = "starting" | "waiting" | "done" | "failed";

export interface BentoVigletActivationDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

/**
 * T958 / §XIV.10.3 — the managed half of the activation card: the device-code
 * dialog that turns AI on in one click.
 *
 * <p>It shows the code to read out and the URL to open, then polls at the
 * interval the service asked for until the credential lands. Nothing sensitive
 * passes through here — approval happens on Viglet's page, and the credential is
 * stored server-side by the T957 completion, which also creates the LLM instance
 * and sets the default. On success the queries every AI surface reads are
 * invalidated, so the "turn AI on" states become working ones without a reload.
 *
 * <p>`SLOW_DOWN` widens the interval by a second and keeps going, per RFC 8628.
 */
export function BentoVigletActivationDialog({
  open,
  onOpenChange,
}: Readonly<BentoVigletActivationDialogProps>) {
  const { t } = useTranslation();
  const refreshAfterActivation = useRefreshAfterActivation();

  const [phase, setPhase] = useState<Phase>("starting");
  const [session, setSession] = useState<TurVigletPairingSession | null>(null);
  /*
   * The failure is held as a key, not as translated text, so the effect below
   * never has to depend on `t` — a `t` whose identity changes would tear the
   * poll loop down and restart it, resetting the code the operator is reading
   * out. Translation happens at render, where it belongs.
   */
  const [failure, setFailure] = useState<{ key: string; fallback: string } | null>(null);

  /*
   * The poll loop is a chain of timeouts rather than an interval so a
   * SLOW_DOWN can widen the gap, and it is cancelled through a ref the effect
   * cleanup flips — a poll in flight when the dialog closes must not write
   * state back into an unmounted tree.
   */
  const cancelledRef = useRef(false);

  useEffect(() => {
    if (!open) return;

    cancelledRef.current = false;
    setPhase("starting");
    setSession(null);
    setFailure(null);

    let timer: number | undefined;

    function fail(key: string, fallback: string) {
      setFailure({ key, fallback });
      setPhase("failed");
    }

    function schedule(deviceCode: string, intervalSeconds: number) {
      timer = window.setTimeout(() => void tick(deviceCode, intervalSeconds), intervalSeconds * 1000);
    }

    async function tick(deviceCode: string, intervalSeconds: number) {
      if (cancelledRef.current) return;
      try {
        const poll = await service.poll(deviceCode);
        if (cancelledRef.current) return;
        switch (poll.state) {
          case "APPROVED":
            setPhase("done");
            refreshAfterActivation();
            return;
          case "DENIED":
            fail("bento.activate.deniedError", "The approval was declined.");
            return;
          case "EXPIRED":
            fail("bento.activate.expiredError", "The code expired. Start again.");
            return;
          case "SLOW_DOWN":
            schedule(deviceCode, intervalSeconds + 1);
            return;
          default:
            schedule(deviceCode, intervalSeconds);
        }
      } catch {
        if (!cancelledRef.current) {
          fail("bento.activate.pollError", "Lost contact with Viglet AI while waiting for approval.");
        }
      }
    }

    service
      .start()
      .then((started) => {
        if (cancelledRef.current) return;
        setSession(started);
        setPhase("waiting");
        schedule(started.deviceCode, Math.max(1, started.intervalSeconds));
      })
      .catch(() => {
        if (!cancelledRef.current) {
          fail("bento.activate.startError", "Could not reach Viglet AI to start the activation.");
        }
      });

    return () => {
      cancelledRef.current = true;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [open, refreshAfterActivation]);

  const verificationUrl = session?.verificationUriComplete || session?.verificationUri;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="bento-shell-header max-w-md overflow-hidden p-0">
        <div className="flex flex-col gap-5 p-6">
          <div className="flex items-center gap-3">
            <TurLogo className="size-8" />
            <DialogTitle className="text-sm font-medium tracking-tight text-muted-foreground">
              {t("bento.activate.dialogTitle", { defaultValue: "Activate Viglet AI" })}
            </DialogTitle>
          </div>

          {phase === "starting" && (
            <div className="flex items-center gap-3 text-sm text-muted-foreground">
              <IconLoader2 className="size-4 animate-spin" aria-hidden />
              {t("bento.activate.starting", { defaultValue: "Contacting Viglet AI…" })}
            </div>
          )}

          {phase === "waiting" && session && (
            <>
              <DialogDescription className="text-sm text-muted-foreground">
                {t("bento.activate.instructions", {
                  defaultValue:
                    "Open the page below, sign in, and enter this code to approve this install.",
                })}
              </DialogDescription>

              <div className="bento-tile bento-glass rounded-2xl px-4 py-5 text-center">
                <span className="font-mono text-3xl font-semibold tracking-[0.3em]">
                  {session.userCode}
                </span>
              </div>

              {verificationUrl && (
                <GradientButton asChild className="w-full">
                  <a href={verificationUrl} target="_blank" rel="noopener noreferrer">
                    <IconExternalLink className="size-4" />
                    {t("bento.activate.openPage", { defaultValue: "Open the approval page" })}
                  </a>
                </GradientButton>
              )}

              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <IconLoader2 className="size-4 animate-spin" aria-hidden />
                {t("bento.activate.waiting", { defaultValue: "Waiting for your approval…" })}
              </div>
            </>
          )}

          {phase === "done" && (
            <>
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
                <IconCircleCheck size={24} />
              </span>
              <div>
                <h2 className="text-xl font-semibold tracking-tight">
                  {t("bento.activate.doneTitle", { defaultValue: "Viglet AI is on" })}
                </h2>
                <DialogDescription className="mt-2 text-sm text-muted-foreground">
                  {t("bento.activate.doneBody", {
                    defaultValue:
                      "Chat, retrieval, agents and semantic navigation are ready. You can switch to your own API key at any time.",
                  })}
                </DialogDescription>
              </div>
            </>
          )}

          {phase === "failed" && (
            <>
              <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-amber-500 to-orange-600 text-white shadow-md">
                <IconAlertTriangle size={24} />
              </span>
              <DialogDescription className="text-sm text-muted-foreground">
                {failure && t(failure.key, { defaultValue: failure.fallback })}
              </DialogDescription>
            </>
          )}

          <div className="flex items-center justify-end gap-2">
            <Button variant={phase === "done" ? "default" : "ghost"} size="sm" onClick={() => onOpenChange(false)}>
              {phase === "done"
                ? t("bento.activate.close", { defaultValue: "Done" })
                : t("common.cancel", { defaultValue: "Cancel" })}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}
