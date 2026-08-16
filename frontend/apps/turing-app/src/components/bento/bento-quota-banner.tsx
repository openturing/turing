import { useSyncExternalStore } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { IconAlertCircle, IconKey, IconSparkles, IconX } from "@tabler/icons-react";

import { ROUTES } from "@/app/routes.const";
import {
  clearQuotaRefusal,
  getQuotaRefusal,
  subscribeQuotaRefusal,
  type QuotaRefusal,
} from "@/lib/quota-refusal";
import { cn } from "@/lib/utils";

/** Subscribes to the refusal published by the axios interceptor (T959). */
export function useQuotaRefusal(): QuotaRefusal | null {
  return useSyncExternalStore(subscribeQuotaRefusal, getQuotaRefusal, getQuotaRefusal);
}

/**
 * T959 / §XIV.10.4 — the banner a quota refusal raises.
 *
 * <p>Running out of the free credit is the only moment where the operator has
 * already seen what the product does and hit the wall at the same time, so it is
 * where the credit is meant to convert. Until now it surfaced as a bare 402 or
 * 429 — a request that simply failed, indistinguishable from a broken install or
 * a provider outage.
 *
 * <p>So this says the plain thing: what ran out, of how much, and when it comes
 * back. Then it offers the same two exits the activation card offers, at the
 * same weight. <b>Bring your own key is never hidden and never styled as the
 * downgrade</b> — a free tier that reads as a cage burns the open-source trust
 * the platform runs on, and that trust is worth more than the conversion.
 *
 * <p>The paid exit appears only when the refusal carried a signed target. An
 * install that shares no secret with the managed service gets the honest version
 * of the banner rather than a link nothing can verify.
 */
export function BentoQuotaBanner({ className }: Readonly<{ className?: string }>) {
  const { t } = useTranslation();
  const refusal = useQuotaRefusal();

  if (!refusal) return null;

  const resourceLabel = t(`bento.quota.resource.${refusal.resource}`, {
    defaultValue: RESOURCE_FALLBACKS[refusal.resource] ?? refusal.resource,
  });

  return (
    <div
      role="status"
      className={cn(
        "bento-tile bento-glass mb-6 rounded-3xl border border-amber-500/30 p-5 md:p-6",
        className,
      )}
    >
      <div className="flex items-start gap-4">
        <span className="grid h-10 w-10 shrink-0 place-items-center rounded-2xl bg-linear-to-br from-amber-500 to-orange-600 text-white shadow-md">
          <IconAlertCircle size={20} />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="text-base font-semibold tracking-tight">
            {t("bento.quota.title", {
              defaultValue: "{{resource}} ran out on this plan",
              resource: resourceLabel,
            })}
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            {t("bento.quota.body", {
              defaultValue: "{{used}} of {{limit}} used.",
              used: refusal.used.toLocaleString(),
              limit: refusal.limit.toLocaleString(),
            })}{" "}
            {refusal.resetsAt
              ? t("bento.quota.resets", {
                  defaultValue: "It resets on {{date}}.",
                  date: formatResetDate(refusal.resetsAt),
                })
              : t("bento.quota.noReset", {
                  defaultValue: "It does not free up on its own.",
                })}
          </p>

          {/*
           * Two exits, one grid, identical weight — same rule the activation
           * card follows. Whichever the operator picks, the product works the
           * same afterwards, and the banner must not suggest otherwise.
           */}
          <div className="mt-4 grid gap-3 sm:grid-cols-2">
            {refusal.upgradeUrl && (
              <a
                href={refusal.upgradeUrl}
                target="_blank"
                rel="noreferrer"
                className="bento-tile bento-tile-clickable flex items-center gap-3 rounded-2xl border border-border/60 bg-card/60 p-4 text-left backdrop-blur"
              >
                <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-linear-to-br from-blue-500 to-indigo-600 text-white">
                  <IconSparkles size={18} />
                </span>
                <span className="text-sm font-medium tracking-tight">
                  {t("bento.quota.upgrade", { defaultValue: "Continue on a paid plan" })}
                </span>
              </a>
            )}
            <Link
              to={`${ROUTES.BENTO_LLM_INSTANCE}/new`}
              onClick={clearQuotaRefusal}
              className="bento-tile bento-tile-clickable flex items-center gap-3 rounded-2xl border border-border/60 bg-card/60 p-4 text-left backdrop-blur"
            >
              <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-linear-to-br from-emerald-500 to-teal-600 text-white">
                <IconKey size={18} />
              </span>
              <span className="text-sm font-medium tracking-tight">
                {t("bento.quota.byok", { defaultValue: "Use my own API key" })}
              </span>
            </Link>
          </div>
        </div>
        <button
          type="button"
          onClick={clearQuotaRefusal}
          aria-label={t("bento.quota.dismiss", { defaultValue: "Dismiss" })}
          className="bento-tile bento-tile-clickable inline-flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-border/60 bg-card/60 backdrop-blur"
        >
          <IconX size={16} />
        </button>
      </div>
    </div>
  );
}

/**
 * The resource keys are the backend's opaque contract, not copy. These are the
 * English fallbacks used until a locale supplies its own `bento.quota.resource.*`.
 */
const RESOURCE_FALLBACKS: Record<string, string> = {
  agents: "AI agents",
  sites: "Semantic navigation sites",
  monthlyTokens: "This month's token budget",
};

/** Locale-formatted reset date; falls back to the raw instant if unparseable. */
function formatResetDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleDateString(undefined, {
    year: "numeric",
    month: "long",
    day: "numeric",
  });
}
