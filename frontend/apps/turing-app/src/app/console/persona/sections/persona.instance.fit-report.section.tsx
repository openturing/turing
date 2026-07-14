"use client"
import { PersonaFitReportSection } from "@/components/persona/persona-fit-report.section"
import type { PersonaFormContext } from "@/components/persona/persona.instance.form.layout"
import { useSectionChrome } from "@/components/ui/section-card"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { useTranslation } from "react-i18next"
import { useOutletContext } from "react-router-dom"

/**
 * "Relatório de adequação de audiência" section (Block AA / T468) — the
 * NotebookLM-style content-fit report. Needs a saved persona (FK), so it
 * renders nothing until the persona has an id.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function PersonaInstanceFitReportSection() {
  const { t } = useTranslation()
  const { value, isNew } = useOutletContext<PersonaFormContext>()
  const chrome = useSectionChrome()
  useSubPageBreadcrumb(t("persona.sidebar.fitReport"))

  if (isNew || !value.id) {
    return null
  }

  return (
    <div className={`w-full px-2 md:px-6 py-2 md:py-8 flex flex-col gap-4${chrome === "bento" ? "" : " max-w-2xl mx-auto"}`}>
      <PersonaFitReportSection personaId={value.id} />
    </div>
  )
}
