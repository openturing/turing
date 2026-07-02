"use client"
import type { LLMFormContext } from "@/components/llm/llm.instance.form.layout"
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { SectionCard } from "@/components/ui/section-card"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { IconBrain } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { useOutletContext } from "react-router-dom"

/**
 * "Configurações do Modelo" section — endpoint URL, model name and API key.
 *
 * @since 2026.3.4
 */
export default function LLMInstanceModelSection() {
  const { t } = useTranslation();
  const { form } = useOutletContext<LLMFormContext>();
  useSubPageBreadcrumb(t("llm.sidebar.model"));

  return (
    <div className="w-full max-w-2xl mx-auto px-2 md:px-6 py-2 md:py-8 flex flex-col gap-4">
      <SectionCard variant="violet">
        <SectionCard.Header icon={IconBrain} title={t("forms.llm.modelSettings")} description={t("forms.llm.modelSettingsDesc")} />
        <SectionCard.Content>
          {/* URL */}
          <div className="w-full">
            <FormField
              control={form.control}
              name="url"
              rules={{ required: t("forms.se.endpointRequired") }}
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.common.endpointUrl")}</FormLabel>
                  <div className="text-muted-foreground text-sm font-normal mt-1">
                    {t("forms.llm.endpointDesc")}
                  </div>
                  <FormControl>
                    <Input placeholder={t("forms.llm.endpointPlaceholder")} type="text" className="w-full" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          {/* Model Name */}
          <div className="w-full">
            <FormField
              control={form.control}
              name="modelName"
              rules={{ required: t("forms.llm.modelName") }}
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.llm.modelName")}</FormLabel>
                  <div className="text-muted-foreground text-sm font-normal mt-1">
                    {t("forms.llm.modelNameDesc")}
                  </div>
                  <FormControl>
                    <Input placeholder={t("forms.llm.modelNamePlaceholder")} type="text" autoComplete="one-time-code" className="w-full" {...field} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          {/* API Key */}
          <div className="w-full">
            <FormField
              control={form.control}
              name="apiKey"
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.common.apiKey")}</FormLabel>
                  <div className="text-muted-foreground text-sm font-normal mt-1">
                    {t("forms.llm.apiKeyDesc")}
                  </div>
                  <FormControl>
                    <Input
                      placeholder={t("forms.llm.apiKeyPlaceholder")}
                      type="text"
                      className="w-full [-webkit-text-security:disc]"
                      autoComplete="one-time-code"
                      {...field}
                      value={field.value ?? ""}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        </SectionCard.Content>
      </SectionCard>
    </div>
  )
}
