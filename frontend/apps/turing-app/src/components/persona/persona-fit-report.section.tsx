"use client"
import { Button } from "@/components/ui/button"
import { SectionCard } from "@/components/ui/section-card"
import type {
  TurContentFitResult,
  TurPersonaFitReport,
} from "@/models/persona/persona-fit.model.ts"
import { TurPersonaFitService } from "@/services/persona/persona-fit.service"
import {
  IconAlertTriangle,
  IconCheck,
  IconRefresh,
  IconReportAnalytics,
  IconSparkles,
} from "@tabler/icons-react"
import { useCallback, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"

const service = new TurPersonaFitService()

/** Red < 40 ≤ amber < 70 ≤ green. */
function band(score: number): { bar: string; text: string } {
  if (score >= 70)
    return { bar: "bg-emerald-500", text: "text-emerald-700 dark:text-emerald-400" }
  if (score >= 40)
    return { bar: "bg-amber-500", text: "text-amber-700 dark:text-amber-400" }
  return { bar: "bg-red-500", text: "text-red-700 dark:text-red-400" }
}

function FitBar({ score }: { score: number }): React.ReactElement {
  const { bar, text } = band(score)
  return (
    <div className="flex items-center gap-2">
      <div className="bg-muted h-2 flex-1 overflow-hidden rounded-full">
        <div
          className={`h-full rounded-full ${bar}`}
          style={{ width: `${Math.max(0, Math.min(100, score))}%` }}
        />
      </div>
      <span className={`w-10 shrink-0 text-right text-sm font-semibold ${text}`}>
        {Math.round(score)}
      </span>
    </div>
  )
}

/**
 * Block AA / §XXVI.5 — the NotebookLM-style "condiz / não condiz" report. Runs
 * the content-fit evaluator over the persona's whole notebook and renders an
 * overall + per-source red/amber/green fit bar (à la T388), with flagged spans
 * and rewrite suggestions. Rendered for AUDIENCE/BOTH personas with sources.
 */
export const PersonaFitReportSection: React.FC<{ personaId: string }> = ({
  personaId,
}) => {
  const { t } = useTranslation()
  const [report, setReport] = useState<TurPersonaFitReport | null>(null)
  const [busy, setBusy] = useState(false)

  const run = useCallback(
    async (regenerate: boolean) => {
      setBusy(true)
      try {
        const result = await service.evaluate(personaId, { regenerate })
        setReport(result)
        if (result.evaluatedCount === 0) {
          toast.error(t("forms.persona.fit.noEvaluable"))
        }
      } catch {
        toast.error(t("forms.persona.fit.failed"))
      } finally {
        setBusy(false)
      }
    },
    [personaId, t]
  )

  return (
    <SectionCard variant="violet">
      <SectionCard.Header
        icon={IconReportAnalytics}
        title={t("forms.persona.fit.title")}
        description={t("forms.persona.fit.desc")}
      />
      <SectionCard.Content>
        <div className="flex flex-wrap items-center gap-2">
          <Button type="button" disabled={busy} onClick={() => run(false)}>
            <IconSparkles className="size-4" />
            {busy ? t("forms.persona.fit.evaluating") : t("forms.persona.fit.evaluate")}
          </Button>
          {report?.canRegenerate && (
            <Button
              type="button"
              variant="outline"
              disabled={busy}
              onClick={() => run(true)}
            >
              <IconRefresh className="size-4" />
              {t("forms.persona.fit.regenerate")}
            </Button>
          )}
        </div>

        {report && (
          <div className="mt-4 flex flex-col gap-4">
            {!report.llmAvailable && (
              <p className="text-muted-foreground flex items-center gap-1 text-xs">
                <IconAlertTriangle className="size-3.5" />
                {t("forms.persona.fit.readabilityOnly")}
              </p>
            )}

            {/* Overall */}
            <div>
              <div className="mb-1 flex items-center justify-between">
                <span className="text-sm font-medium">
                  {t("forms.persona.fit.overall")}
                </span>
                <span className="text-muted-foreground text-xs">
                  {t("forms.persona.fit.evaluatedOf", {
                    evaluated: report.evaluatedCount,
                    total: report.totalSources,
                  })}
                </span>
              </div>
              <FitBar score={report.overallScore} />
            </div>

            {/* Per source */}
            <ul className="flex flex-col gap-4">
              {report.sources.map((s) => (
                <SourceReport key={s.sourceId ?? s.sourceName} result={s} />
              ))}
            </ul>
          </div>
        )}
      </SectionCard.Content>
    </SectionCard>
  )
}

function SourceReport({
  result,
}: {
  result: TurContentFitResult
}): React.ReactElement {
  const { t } = useTranslation()
  return (
    <li className="rounded-lg border p-3">
      <div className="mb-1 font-medium">
        {result.sourceName ?? t("forms.persona.fit.untitled")}
      </div>
      <FitBar score={result.fitScore} />
      {result.summary && (
        <p className="text-muted-foreground mt-2 text-sm">{result.summary}</p>
      )}
      {result.error && (
        <p className="mt-1 text-xs text-amber-600 dark:text-amber-400">
          {result.error}
        </p>
      )}
      {result.fits?.length > 0 && (
        <ul className="mt-2 flex flex-col gap-1">
          {result.fits.map((f, i) => (
            <li key={i} className="flex items-start gap-1.5 text-sm">
              <IconCheck className="mt-0.5 size-4 shrink-0 text-emerald-600" />
              <span>{f}</span>
            </li>
          ))}
        </ul>
      )}
      {result.misfits?.length > 0 && (
        <ul className="mt-2 flex flex-col gap-2">
          {result.misfits.map((m, i) => (
            <li
              key={i}
              className="rounded-md border-l-2 border-red-400 bg-red-500/5 p-2 text-sm"
            >
              <div className="flex items-center gap-2">
                <span className="font-medium">“{m.span}”</span>
                <span className="rounded-full bg-red-500/15 px-2 py-0.5 text-xs text-red-700 dark:text-red-400">
                  {t(`persona.misfitReason.${m.reason}`, { defaultValue: m.reason })}
                </span>
              </div>
              {m.suggestion && (
                <div className="text-muted-foreground mt-1 text-xs">
                  → {m.suggestion}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </li>
  )
}
