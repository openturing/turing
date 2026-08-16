import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { IconAlertTriangle, IconCheck, IconRefresh, IconShieldLock } from "@tabler/icons-react";

import { GradientButton } from "@/components/ui/gradient-button";
import { SectionCard } from "@/components/ui/section-card";
import type { TurVigletKeyRotation } from "@/models/genai/viglet-ai.model";
import { TurVigletAiService } from "@/services/genai/viglet-ai.service";

const service = new TurVigletAiService();

/**
 * T965 / §LXXIV — rotating this install's Viglet AI signing key.
 *
 * <p>The T960 binding is what keeps the managed credential from being a
 * resellable inference key, and its whole value is that it can be revoked for
 * one install. Until now revoking meant deleting a config var by hand — a
 * runbook step nobody finds while a key is leaking.
 *
 * <p>Modelled on the URL-signing-secret section one card over, with the one
 * difference that matters: the server registers the new key with the managed
 * service <b>before</b> swapping, so a refusal is reported here as "nothing
 * changed" rather than leaving the install unable to authenticate.
 *
 * <p>Renders nothing on an install that never paired — there is no binding to
 * rotate, and an action that always fails is worse than no action.
 *
 * @since 2026.3.4
 */
export function VigletInstallKeySection() {
  const { t } = useTranslation();
  const [paired, setPaired] = useState<boolean | null>(null);
  const [rotating, setRotating] = useState(false);
  const [result, setResult] = useState<TurVigletKeyRotation | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    service
      .status()
      .then((status) => {
        if (active) setPaired(status.paired);
      })
      .catch(() => {
        if (active) setPaired(false);
      });
    return () => {
      active = false;
    };
  }, []);

  const onRotate = useCallback(async () => {
    setRotating(true);
    setFailed(false);
    try {
      setResult(await service.rotateKey());
    } catch {
      // Unreachable endpoint, not a refusal — the key is untouched either way,
      // and saying so is the only useful thing this surface can do about it.
      setResult(null);
      setFailed(true);
    } finally {
      setRotating(false);
    }
  }, []);

  if (!paired) return null;

  return (
    <SectionCard variant="rose">
      <SectionCard.Header
        icon={IconShieldLock}
        title={t("globalSettings.vigletInstallKeyTitle", {
          defaultValue: "Viglet AI install key",
        })}
        description={t("globalSettings.vigletInstallKeyDesc", {
          defaultValue:
            "The signing key that binds your managed credential to this install. Rotate it if the key may have leaked — the new key is registered with Viglet AI before it replaces the old one.",
        })}
      />
      <SectionCard.Content>
        {/*
         * Plain two-column markup rather than the design system's
         * FormItemTwoColumns: that primitive reads react-hook-form context, and
         * this card holds a status line and a button, not a field. Borrowing it
         * would tie a self-contained section to whatever form happens to be
         * above it.
         */}
        <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="min-w-0 flex-1">
            <p className="text-sm font-medium">
              {t("globalSettings.vigletInstallKeyStatusLabel", { defaultValue: "Status" })}
            </p>
            <div className="mt-1 text-sm text-muted-foreground">
              {t("globalSettings.vigletInstallKeyStatusBound", {
                defaultValue:
                  "This install signs every managed call. The private half never leaves it and is never shown here.",
              })}
              {result && (
                <span className="mt-1 block text-xs text-muted-foreground">{result.detail}</span>
              )}
              {failed && (
                <span className="mt-1 block text-xs text-amber-600 dark:text-amber-400">
                  {t("globalSettings.vigletInstallKeyUnreachable", {
                    defaultValue:
                      "Viglet AI could not be reached. Nothing was rotated — the current key still works.",
                  })}
                </span>
              )}
            </div>
          </div>
          <div className="shrink-0">
            <div className="flex items-center gap-2">
              {result?.rotated && (
                <span className="inline-flex items-center gap-1 text-xs text-emerald-600 dark:text-emerald-400">
                  <IconCheck className="size-4" />
                  {t("globalSettings.vigletInstallKeyRotatedBadge", { defaultValue: "Rotated" })}
                </span>
              )}
              {result && !result.rotated && (
                <span className="inline-flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400">
                  <IconAlertTriangle className="size-4" />
                  {t("globalSettings.vigletInstallKeyRefusedBadge", { defaultValue: "Unchanged" })}
                </span>
              )}
              <GradientButton
                type="button"
                variant="outline"
                size="sm"
                onClick={onRotate}
                loading={rotating}
                disabled={rotating}
              >
                <IconRefresh className="size-4" />
                {t("globalSettings.vigletInstallKeyRotateAction", {
                  defaultValue: "Rotate key",
                })}
              </GradientButton>
            </div>
          </div>
        </div>
      </SectionCard.Content>
    </SectionCard>
  );
}
