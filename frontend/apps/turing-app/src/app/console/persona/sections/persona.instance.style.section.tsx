"use client"
import type { PersonaFormContext } from "@/components/persona/persona.instance.form.layout"
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { SectionCard } from "@/components/ui/section-card"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { IconPalette } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { useOutletContext } from "react-router-dom"

const NONE_VALUE = "__none__"
const TONES = ["FORMAL", "CASUAL", "TECHNICAL", "EXECUTIVE"] as const
const LANGUAGE_STYLES = [
  "NEUTRAL",
  "DIRECT",
  "NARRATIVE",
  "PERSUASIVE",
  "INSTRUCTIONAL",
] as const

/**
 * "Diretrizes de Estilo" section of the persona editor — tone, verbosity and
 * language style.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function PersonaInstanceStyleSection() {
  const { t } = useTranslation()
  const { form } = useOutletContext<PersonaFormContext>()
  useSubPageBreadcrumb(t("persona.sidebar.style"))

  return (
    <div className="w-full max-w-2xl mx-auto px-2 md:px-6 py-2 md:py-8 flex flex-col gap-4">
      <SectionCard variant="emerald">
        <SectionCard.Header
          icon={IconPalette}
          title={t("forms.persona.styleGuidelines")}
          description={t("forms.persona.styleGuidelinesDesc")}
        />
        <SectionCard.Content>
          <FormField
            control={form.control}
            name="tone"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.tone")}</FormLabel>
                <FormControl>
                  <Select
                    value={field.value ?? NONE_VALUE}
                    onValueChange={(v) => field.onChange(v === NONE_VALUE ? null : v)}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue placeholder={t("forms.persona.tonePlaceholder")} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE_VALUE}>
                        {t("forms.persona.tonePlaceholder")}
                      </SelectItem>
                      {TONES.map((tone) => (
                        <SelectItem key={tone} value={tone}>
                          {t(`persona.tones.${tone}`)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormControl>
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="verbosity"
            rules={{
              min: { value: 1, message: t("forms.persona.verbosityRange") },
              max: { value: 5, message: t("forms.persona.verbosityRange") },
            }}
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.verbosity")}</FormLabel>
                <FormControl>
                  <Input
                    {...field}
                    type="number"
                    min={1}
                    max={5}
                    value={field.value ?? 3}
                    onChange={(e) => field.onChange(Number(e.target.value))}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="languageStyle"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.languageStyle")}</FormLabel>
                <FormControl>
                  <Select
                    value={field.value ?? NONE_VALUE}
                    onValueChange={(v) => field.onChange(v === NONE_VALUE ? null : v)}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue
                        placeholder={t("forms.persona.languageStylePlaceholder")}
                      />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE_VALUE}>
                        {t("forms.persona.languageStylePlaceholder")}
                      </SelectItem>
                      {LANGUAGE_STYLES.map((style) => (
                        <SelectItem key={style} value={style}>
                          {t(`persona.languageStyles.${style}`)}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormControl>
              </FormItem>
            )}
          />
        </SectionCard.Content>
      </SectionCard>
    </div>
  )
}
