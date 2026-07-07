"use client"
import {
  ContentFitDetails,
  FitBar,
} from "@/components/persona/content-fit-result.view"
import { Button } from "@/components/ui/button"
import { SectionCard } from "@/components/ui/section-card"
import type {
  TurContentFitResult,
  TurPersonaFitReport,
} from "@/models/persona/persona-fit.model.ts"
import { TurPersonaFitService } from "@/services/persona/persona-fit.service"
import {
  IconAlertTriangle,
  IconRefresh,
  IconReportAnalytics,
  IconSparkles,
} from "@tabler/icons-react"
import { useCallback, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"

const service = new TurPersonaFitService()

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
      <ContentFitDetails result={result} />
    </li>
  )
}
