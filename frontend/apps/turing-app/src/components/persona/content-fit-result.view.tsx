"use client"
import type { TurContentFitResult } from "@/models/persona/persona-fit.model.ts"
import { IconCheck } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"

/** Red < 40 ≤ amber < 70 ≤ green. Shared by the fit report (T468) and the
 * auto-suggest ranking (T471). */
export function fitBand(score: number): { bar: string; text: string } {
  if (score >= 70)
    return { bar: "bg-emerald-500", text: "text-emerald-700 dark:text-emerald-400" }
  if (score >= 40)
    return { bar: "bg-amber-500", text: "text-amber-700 dark:text-amber-400" }
  return { bar: "bg-red-500", text: "text-red-700 dark:text-red-400" }
}

/** A red/amber/green fit bar with the rounded score (à la T388 coverage bars). */
export function FitBar({ score }: { score: number }): React.ReactElement {
  const { bar, text } = fitBand(score)
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

/** The qualitative "condiz / não condiz" body of one content-fit verdict:
 * summary, optional error note, the fits[] bullets and the misfits[] flagged
 * spans with reason chips + rewrite suggestions. */
export function ContentFitDetails({
  result,
}: {
  result: TurContentFitResult
}): React.ReactElement {
  const { t } = useTranslation()
  return (
    <>
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
    </>
  )
}
