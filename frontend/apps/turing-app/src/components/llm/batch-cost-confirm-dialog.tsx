"use client";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { GradientButton } from "@/components/ui/gradient-button";
import type {
  TurBatchCostEstimate,
  TurBatchCostRequest,
} from "@/models/cost-governance/cost-governance.model";
import { TurCostGovernanceService } from "@/services/cost-governance/cost-governance.service";
import { IconAlertTriangle, IconLoader2 } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

const service = new TurCostGovernanceService();

function formatUsd(n: number): string {
  if (n === 0) return "$0.00";
  if (n < 1) return `$${n.toFixed(4)}`;
  return `$${n.toFixed(2)}`;
}

/**
 * T783 — a reusable pre-flight cost gate for an expensive batch run (eval
 * dataset, research study, batch re-embed). When `open`, it quotes the projected
 * cost via `/v2/llm/cost/preflight-batch` and lets the operator proceed or
 * cancel. Reusable across runners — pass the batch's `params`; the runner keeps
 * its own launch logic in `onProceed`.
 */
export function BatchCostConfirmDialog({
  open,
  params,
  title,
  onProceed,
  onCancel,
}: Readonly<{
  open: boolean;
  params: TurBatchCostRequest | null;
  title?: string;
  onProceed: () => void;
  onCancel: () => void;
}>) {
  const { t } = useTranslation();
  const [estimate, setEstimate] = useState<TurBatchCostEstimate | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!open || !params) {
      setEstimate(null);
      return;
    }
    let cancelled = false;
    setLoading(true);
    service
      .preflightBatch(params)
      .then((e) => {
        if (!cancelled) setEstimate(e);
      })
      .catch(() => {
        if (!cancelled) setEstimate(null);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [open, params]);

  return (
    <Dialog open={open} onOpenChange={(next) => !next && onCancel()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title ?? t("batchCost.title")}</DialogTitle>
          <DialogDescription>{t("batchCost.description")}</DialogDescription>
        </DialogHeader>

        {loading && (
          <div className="text-muted-foreground flex items-center gap-2 py-4 text-sm">
            <IconLoader2 className="size-4 animate-spin" />
            {t("batchCost.estimating")}
          </div>
        )}

        {!loading && estimate && (
          <div className="space-y-3 py-2">
            <div className="flex items-baseline justify-between">
              <span className="text-muted-foreground text-sm">{t("batchCost.projectedTotal")}</span>
              <span className="text-2xl font-semibold">{formatUsd(estimate.totalCostUsd)}</span>
            </div>
            <p className="text-muted-foreground text-xs">
              {t("batchCost.breakdown", {
                items: estimate.itemCount.toLocaleString(),
                perItem: formatUsd(estimate.perItemCostUsd),
              })}
            </p>
            {!estimate.priced && (
              <p className="flex items-center gap-1.5 text-xs text-amber-600 dark:text-amber-400">
                <IconAlertTriangle className="size-3.5" />
                {t("batchCost.unpriced")}
              </p>
            )}
            {estimate.overBudget && (
              <p className="flex items-center gap-1.5 text-xs text-amber-600 dark:text-amber-400">
                <IconAlertTriangle className="size-3.5" />
                {t("batchCost.overBudget", { budget: formatUsd(estimate.budgetUsd ?? 0) })}
              </p>
            )}
          </div>
        )}

        {!loading && !estimate && !params && null}

        <DialogFooter>
          <Button variant="outline" onClick={onCancel}>{t("batchCost.cancel")}</Button>
          <GradientButton onClick={onProceed} disabled={loading}>{t("batchCost.proceed")}</GradientButton>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
