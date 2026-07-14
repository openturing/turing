"use client"
import type { PersonaFormContext } from "@/components/persona/persona.instance.form.layout"
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
} from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { SectionCard, useSectionChrome } from "@/components/ui/section-card"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { IconUsers } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { useOutletContext } from "react-router-dom"

const NONE_VALUE = "__none__"
const READING_LEVELS = [
  "ELEMENTARY",
  "MIDDLE",
  "SECONDARY",
  "UNDERGRADUATE",
  "GRADUATE",
] as const
const DOMAIN_EXPERTISE = [
  "NOVICE",
  "BEGINNER",
  "INTERMEDIATE",
  "ADVANCED",
  "EXPERT",
] as const

/**
 * "Perfil de audiência" section (Block AA) — the reader proxy read only by the
 * content-fit evaluation path. Only surfaced in the sidebar for AUDIENCE / BOTH
 * personas.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function PersonaInstanceAudienceSection() {
  const { t } = useTranslation()
  const { form } = useOutletContext<PersonaFormContext>()
  const chrome = useSectionChrome()
  useSubPageBreadcrumb(t("persona.sidebar.audience"))

  return (
    <div className={`w-full px-2 md:px-6 py-2 md:py-8 flex flex-col gap-4${chrome === "bento" ? "" : " max-w-2xl mx-auto"}`}>
      <SectionCard variant="slate">
        <SectionCard.Header
          icon={IconUsers}
          title={t("forms.persona.audience")}
          description={t("forms.persona.audienceDesc")}
        />
        <SectionCard.Content>
          <FormField
            control={form.control}
            name="audience.readingLevel"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.readingLevel")}</FormLabel>
                <FormControl>
                  <Select
                    value={field.value ?? NONE_VALUE}
                    onValueChange={(v) => field.onChange(v === NONE_VALUE ? null : v)}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue
                        placeholder={t("forms.persona.readingLevelPlaceholder")}
                      />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE_VALUE}>
                        {t("forms.persona.readingLevelPlaceholder")}
                      </SelectItem>
                      {READING_LEVELS.map((level) => (
                        <SelectItem key={level} value={level}>
                          {t(`persona.readingLevels.${level}`)}
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
            name="audience.domainExpertise"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.domainExpertise")}</FormLabel>
                <FormControl>
                  <Select
                    value={field.value ?? NONE_VALUE}
                    onValueChange={(v) => field.onChange(v === NONE_VALUE ? null : v)}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue
                        placeholder={t("forms.persona.domainExpertisePlaceholder")}
                      />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE_VALUE}>
                        {t("forms.persona.domainExpertisePlaceholder")}
                      </SelectItem>
                      {DOMAIN_EXPERTISE.map((level) => (
                        <SelectItem key={level} value={level}>
                          {t(`persona.domainExpertise.${level}`)}
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
            name="audience.primaryLanguage"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.primaryLanguage")}</FormLabel>
                <FormControl>
                  <Input
                    {...field}
                    value={field.value ?? ""}
                    maxLength={8}
                    placeholder={t("forms.persona.primaryLanguagePlaceholder")}
                  />
                </FormControl>
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="audience.vocabularyCeiling"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.vocabularyCeiling")}</FormLabel>
                <div className="text-muted-foreground text-sm font-normal mt-1">
                  {t("forms.persona.vocabularyCeilingDesc")}
                </div>
                <FormControl>
                  <Textarea
                    {...field}
                    value={field.value ?? ""}
                    rows={2}
                    placeholder={t("forms.persona.vocabularyCeilingPlaceholder")}
                  />
                </FormControl>
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="audience.comprehensionNotes"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.comprehensionNotes")}</FormLabel>
                <FormControl>
                  <Textarea
                    {...field}
                    value={field.value ?? ""}
                    rows={2}
                    placeholder={t("forms.persona.comprehensionNotesPlaceholder")}
                  />
                </FormControl>
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="audience.accessibilityNotes"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.accessibilityNotes")}</FormLabel>
                <FormControl>
                  <Textarea
                    {...field}
                    value={field.value ?? ""}
                    rows={2}
                    placeholder={t("forms.persona.accessibilityNotesPlaceholder")}
                  />
                </FormControl>
              </FormItem>
            )}
          />
        </SectionCard.Content>
      </SectionCard>
    </div>
  )
}
