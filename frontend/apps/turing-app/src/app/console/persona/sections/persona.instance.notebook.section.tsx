"use client"
import { PersonaSourcesSection } from "@/components/persona/persona-sources.section"
import type { PersonaFormContext } from "@/components/persona/persona.instance.form.layout"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { useTranslation } from "react-i18next"
import { useOutletContext } from "react-router-dom"

/**
 * "Caderno de avaliação" section (Block AA / T465) — the persona's evaluation
 * notebook. Needs a saved persona (FK) and is only meaningful for audience
 * personas, so it renders nothing until the persona has an id.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function PersonaInstanceNotebookSection() {
  const { t } = useTranslation()
  const { value, isNew } = useOutletContext<PersonaFormContext>()
  useSubPageBreadcrumb(t("persona.sidebar.notebook"))

  if (isNew || !value.id) {
    return null
  }

  return (
    <div className="w-full max-w-2xl mx-auto px-2 md:px-6 py-2 md:py-8 flex flex-col gap-4">
      <PersonaSourcesSection personaId={value.id} />
    </div>
  )
}
