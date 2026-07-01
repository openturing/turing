"use client"
import type { LLMFormContext } from "@/components/llm/llm.instance.form.layout"
import { LLMInstanceCapabilitiesForm } from "@/components/llm/llm.instance.capabilities.form"
import { SectionCard } from "@/components/ui/section-card"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { IconSparkles } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { useOutletContext } from "react-router-dom"

/**
 * "Capacidades Nativas" section — the per-instance native capability matrix.
 * The capabilities form saves independently of the parent layout's form, so
 * this section is only meaningful for an existing instance.
 *
 * @since 2026.3.4
 */
export default function LLMInstanceCapabilitiesSection() {
  const { t } = useTranslation();
  const { value, isNew, readOnly, selectedVendorId } = useOutletContext<LLMFormContext>();
  useSubPageBreadcrumb(t("llm.sidebar.capabilities"));

  return (
    <div className="w-full max-w-2xl mx-auto px-2 md:px-6 py-2 md:py-8 flex flex-col gap-4">
      {!isNew && value.id ? (
        <LLMInstanceCapabilitiesForm
          instanceId={value.id}
          vendorId={selectedVendorId}
          readOnly={readOnly}
        />
      ) : null}
      {/* Fallback when the vendor exposes no native capabilities (the form
          self-renders nothing), so the section is never blank. `only:block`
          shows it only when it is the sole child — i.e. the form rendered null. */}
      <div className="hidden only:block">
        <SectionCard variant="violet">
          <SectionCard.Header
            icon={IconSparkles}
            title={t("forms.llm.nativeCapabilities")}
            description={t("forms.llm.nativeCapabilitiesDesc")}
          />
          <SectionCard.Content>
            <p className="text-sm text-muted-foreground">
              {t("forms.llm.nativeCapabilitiesEmpty", {
                defaultValue: "This vendor has no native capabilities to configure.",
              })}
            </p>
          </SectionCard.Content>
        </SectionCard>
      </div>
    </div>
  )
}
