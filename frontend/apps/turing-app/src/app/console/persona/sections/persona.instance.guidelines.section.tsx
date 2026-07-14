"use client"
import type { PersonaFormContext } from "@/components/persona/persona.instance.form.layout"
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
} from "@/components/ui/form"
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
import { IconBan, IconBookmark, IconMessage2 } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { useOutletContext } from "react-router-dom"

const NONE_VALUE = "__none__"

/**
 * "Diretrizes da Persona" section of the persona editor — vocabulary
 * (mandatory / forbidden terms), the few-shot repository store and the brand
 * context MCP server.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function PersonaInstanceGuidelinesSection() {
  const { t } = useTranslation()
  const { form, stores, mcpServers } = useOutletContext<PersonaFormContext>()
  const chrome = useSectionChrome()
  useSubPageBreadcrumb(t("persona.sidebar.guidelines"))

  return (
    <div className={`w-full px-2 md:px-6 py-2 md:py-8 flex flex-col gap-4${chrome === "bento" ? "" : " max-w-2xl mx-auto"}`}>
      {/* Vocabulary */}
      <SectionCard variant="amber">
        <SectionCard.Header
          icon={IconBan}
          title={t("forms.persona.vocabulary")}
          description={t("forms.persona.vocabularyDesc")}
        />
        <SectionCard.Content>
          <FormField
            control={form.control}
            name="mandatoryTermsText"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.mandatoryTerms")}</FormLabel>
                <div className="text-muted-foreground text-sm font-normal mt-1">
                  {t("forms.persona.termsDesc")}
                </div>
                <FormControl>
                  <Textarea
                    {...field}
                    rows={3}
                    placeholder={t("forms.persona.mandatoryTermsPlaceholder")}
                  />
                </FormControl>
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="forbiddenTermsText"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.forbiddenTerms")}</FormLabel>
                <div className="text-muted-foreground text-sm font-normal mt-1">
                  {t("forms.persona.termsDesc")}
                </div>
                <FormControl>
                  <Textarea
                    {...field}
                    rows={3}
                    placeholder={t("forms.persona.forbiddenTermsPlaceholder")}
                  />
                </FormControl>
              </FormItem>
            )}
          />
        </SectionCard.Content>
      </SectionCard>

      {/* Few-Shot Repository */}
      <SectionCard variant="cyan">
        <SectionCard.Header
          icon={IconBookmark}
          title={t("forms.persona.fewShot")}
          description={t("forms.persona.fewShotDesc")}
        />
        <SectionCard.Content>
          <FormField
            control={form.control}
            name="fewShotStore"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.fewShotStore")}</FormLabel>
                <FormControl>
                  <Select
                    value={field.value?.id ?? NONE_VALUE}
                    onValueChange={(v) => {
                      if (v === NONE_VALUE) {
                        field.onChange(null)
                      } else {
                        const store = stores.find((s) => s.id === v)
                        field.onChange(store ?? null)
                      }
                    }}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue
                        placeholder={t("forms.persona.fewShotStorePlaceholder")}
                      />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE_VALUE}>
                        {t("forms.persona.fewShotStorePlaceholder")}
                      </SelectItem>
                      {stores.map((store) => (
                        <SelectItem key={store.id} value={store.id}>
                          {store.title}
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

      {/* Brand Context MCP */}
      <SectionCard variant="rose">
        <SectionCard.Header
          icon={IconMessage2}
          title={t("forms.persona.brandContext")}
          description={t("forms.persona.brandContextDesc")}
        />
        <SectionCard.Content>
          <FormField
            control={form.control}
            name="brandContextMcpServer"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.brandContextMcp")}</FormLabel>
                <FormControl>
                  <Select
                    value={field.value?.id ?? NONE_VALUE}
                    onValueChange={(v) => {
                      if (v === NONE_VALUE) {
                        field.onChange(null)
                      } else {
                        const mcp = mcpServers.find((m) => m.id === v)
                        field.onChange(mcp ?? null)
                      }
                    }}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue
                        placeholder={t("forms.persona.brandContextMcpPlaceholder")}
                      />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value={NONE_VALUE}>
                        {t("forms.persona.brandContextMcpPlaceholder")}
                      </SelectItem>
                      {mcpServers.map((mcp) => (
                        <SelectItem key={mcp.id} value={mcp.id}>
                          {mcp.title}
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
