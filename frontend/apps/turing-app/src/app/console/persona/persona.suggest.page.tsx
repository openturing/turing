"use client"
import { usePersonas } from "@/api/queries/persona.queries"
import { ROUTES } from "@/app/routes.const"
import {
  ContentFitDetails,
  FitBar,
} from "@/components/persona/content-fit-result.view"
import { Button } from "@/components/ui/button"
import { Checkbox } from "@/components/ui/checkbox"
import { SectionCard } from "@/components/ui/section-card"
import { Textarea } from "@/components/ui/textarea"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import type { TurPersona } from "@/models/persona/persona.model.ts"
import type { TurPersonaSuggestionReport } from "@/models/persona/persona-suggestion.model.ts"
import { TurPersonaSuggestionService } from "@/services/persona/persona-suggestion.service"
import {
  IconAlertTriangle,
  IconRefresh,
  IconSparkles,
  IconTargetArrow,
  IconTrophy,
} from "@tabler/icons-react"
import { useCallback, useMemo, useState } from "react"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"

const service = new TurPersonaSuggestionService()

/** True for AUDIENCE / BOTH personas — the only ones the evaluator can grade. */
function isAudience(p: TurPersona): boolean {
  return p.personaKind === "AUDIENCE" || p.personaKind === "BOTH"
}

/**
 * Block AA / §XXVI.8 — auto-suggest the best-fit audience persona for a piece of
 * content. Paste content, optionally restrict to a subset of audience personas,
 * and the backend batches the T467 content-fit evaluator and returns the
 * personas ranked best-fit first (red/amber/green bars à la T388). Answers
 * "which persona is this content even *for*", feeding editorial routing and
 * Block Z A/B persona selection.
 */
export default function PersonaSuggestPage() {
  const { t } = useTranslation()
  useSubPageBreadcrumb({ label: t("forms.persona.suggest.title") })

  const { data: personas } = usePersonas()
  const audiencePersonas = useMemo(
    () => (personas ?? []).filter(isAudience),
    [personas]
  )

  const [content, setContent] = useState("")
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [report, setReport] = useState<TurPersonaSuggestionReport | null>(null)
  const [busy, setBusy] = useState(false)

  const toggle = useCallback((id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }, [])

  const run = useCallback(
    async (regenerate: boolean) => {
      if (!content.trim()) {
        toast.error(t("forms.persona.suggest.noContent"))
        return
      }
      setBusy(true)
      try {
        const ids = selectedIds.size > 0 ? [...selectedIds] : undefined
        const result = await service.suggest(content, { personaIds: ids, regenerate })
        setReport(result)
        if (result.evaluatedCount === 0) {
          toast.error(t("forms.persona.suggest.noPersonas"))
        }
      } catch {
        toast.error(t("forms.persona.suggest.failed"))
      } finally {
        setBusy(false)
      }
    },
    [content, selectedIds, t]
  )

  return (
    <div className="flex flex-col gap-4">
      <SectionCard variant="violet">
        <SectionCard.Header
          icon={IconTargetArrow}
          title={t("forms.persona.suggest.title")}
          description={t("forms.persona.suggest.desc")}
        />
        <SectionCard.Content>
          <label className="mb-1 block text-sm font-medium">
            {t("forms.persona.suggest.contentLabel")}
          </label>
          <Textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={8}
            placeholder={t("forms.persona.suggest.contentPlaceholder")}
          />

          {audiencePersonas.length > 0 && (
            <div className="mt-4">
              <p className="text-muted-foreground mb-2 text-xs">
                {t("forms.persona.suggest.restrictLabel")}
              </p>
              <div className="flex flex-wrap gap-3">
                {audiencePersonas.map((p) => (
                  <label
                    key={p.id}
                    className="flex cursor-pointer items-center gap-2 text-sm"
                  >
                    <Checkbox
                      checked={p.id ? selectedIds.has(p.id) : false}
                      onCheckedChange={() => p.id && toggle(p.id)}
                    />
                    {p.name}
                  </label>
                ))}
              </div>
            </div>
          )}

          <div className="mt-4 flex flex-wrap items-center gap-2">
            <Button type="button" disabled={busy} onClick={() => run(false)}>
              <IconSparkles className="size-4" />
              {busy
                ? t("forms.persona.suggest.evaluating")
                : t("forms.persona.suggest.evaluate")}
            </Button>
            {report && (
              <Button
                type="button"
                variant="outline"
                disabled={busy}
                onClick={() => run(true)}
              >
                <IconRefresh className="size-4" />
                {t("forms.persona.suggest.regenerate")}
              </Button>
            )}
          </div>

          {audiencePersonas.length === 0 && personas && (
            <p className="text-muted-foreground mt-3 flex items-center gap-1 text-xs">
              <IconAlertTriangle className="size-3.5" />
              {t("forms.persona.suggest.noPersonasHint")}
            </p>
          )}
        </SectionCard.Content>
      </SectionCard>

      {report && report.rankings.length > 0 && (
        <SectionCard variant="emerald">
          <SectionCard.Header
            icon={IconTrophy}
            title={t("forms.persona.suggest.rankingTitle")}
            description={t("forms.persona.suggest.considered", {
              count: report.evaluatedCount,
            })}
          />
          <SectionCard.Content>
            {!report.llmAvailable && (
              <p className="text-muted-foreground mb-3 flex items-center gap-1 text-xs">
                <IconAlertTriangle className="size-3.5" />
                {t("forms.persona.suggest.readabilityOnly")}
              </p>
            )}
            <ul className="flex flex-col gap-3">
              {report.rankings.map((s) => {
                const isBest = s.rank === 1
                return (
                  <li
                    key={s.personaId}
                    className={`rounded-lg border p-3 ${
                      isBest ? "border-emerald-400 bg-emerald-500/5" : ""
                    }`}
                  >
                    <div className="mb-1 flex items-center gap-2">
                      <span className="text-muted-foreground w-7 shrink-0 text-sm font-semibold">
                        #{s.rank}
                      </span>
                      <a
                        href={`${ROUTES.PERSONA_INSTANCE}/${s.personaId}`}
                        className="font-medium hover:underline"
                      >
                        {s.personaName}
                      </a>
                      {isBest && (
                        <span className="rounded-full bg-emerald-500/15 px-2 py-0.5 text-xs font-medium text-emerald-700 dark:text-emerald-400">
                          {t("forms.persona.suggest.bestFit")}
                        </span>
                      )}
                    </div>
                    <FitBar score={s.fitScore} />
                    <ContentFitDetails result={s.result} />
                  </li>
                )
              })}
            </ul>
          </SectionCard.Content>
        </SectionCard>
      )}
    </div>
  )
}
