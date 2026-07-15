"use client"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import type { TurModelVerifyResult } from "@/models/verify.model"
import {
  IconCircleCheck,
  IconCircleX,
  IconLoader2,
  IconPlugConnected,
} from "@tabler/icons-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"

interface Props {
  /**
   * Fires the live probe against the saved instance and resolves to the
   * backend result. Errors are caught here and rendered as a failure badge.
   */
  onVerify: () => Promise<TurModelVerifyResult>
  /** When set, the button is disabled and `disabledHint` is shown beside it. */
  disabled?: boolean
  disabledHint?: string
}

/**
 * Reusable "Verify" control for the LLM-instance and embedding-model forms.
 * Renders a button that fires a minimal live call against the saved model and
 * shows a success/error badge with the round-trip latency, so an operator can
 * confirm the vendor/URL/model/key configuration actually works.
 */
export const ModelVerifyButton: React.FC<Props> = ({ onVerify, disabled, disabledHint }) => {
  const { t } = useTranslation()
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<TurModelVerifyResult | null>(null)

  const run = async () => {
    setLoading(true)
    setResult(null)
    try {
      setResult(await onVerify())
    } catch {
      setResult({ ok: false, message: t("forms.common.verifyFailed"), latencyMs: 0 })
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-3">
        <Button type="button" variant="outline" onClick={run} disabled={disabled ?? loading}>
          {loading ? (
            <IconLoader2 className="size-4 animate-spin" />
          ) : (
            <IconPlugConnected className="size-4" />
          )}
          {t("forms.common.verify")}
        </Button>

        {disabled && disabledHint && (
          <span className="text-sm text-muted-foreground">{disabledHint}</span>
        )}

        {!loading && result && (
          <span
            className={cn(
              "inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-xs font-semibold",
              result.ok
                ? "bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
                : "bg-red-500/10 text-red-600 dark:text-red-400",
            )}
          >
            {result.ok ? (
              <IconCircleCheck className="size-4" />
            ) : (
              <IconCircleX className="size-4" />
            )}
            {result.ok
              ? t("forms.common.verifyOk", { ms: result.latencyMs })
              : t("forms.common.verifyError")}
          </span>
        )}
      </div>

      {!loading && result?.message && (
        <p
          className={cn(
            "text-sm",
            result.ok ? "text-muted-foreground" : "text-red-600 dark:text-red-400",
          )}
        >
          {result.message}
        </p>
      )}
    </div>
  )
}
