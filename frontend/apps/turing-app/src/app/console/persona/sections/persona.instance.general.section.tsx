"use client"
import type { PersonaFormContext } from "@/components/persona/persona.instance.form.layout"
import { Button } from "@/components/ui/button"
import {
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { GradientSwitch } from "@/components/ui/gradient-switch"
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
import {
  IconAlertTriangle,
  IconSettings,
  IconToggleLeft,
  IconTrash,
} from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { useOutletContext } from "react-router-dom"

const PERSONA_KINDS = ["SPEAKER", "AUDIENCE", "BOTH"] as const

/**
 * "Geral" section of the persona editor — general info, status toggle and the
 * danger zone. Shares the parent layout's single form instance; the delete
 * confirmation dialog itself lives in the internal sidebar (shared `open`
 * state), so the danger-zone button only flips that state.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function PersonaInstanceGeneralSection() {
  const { t } = useTranslation()
  const { form, isNew, setDeleteOpen } = useOutletContext<PersonaFormContext>()
  const chrome = useSectionChrome()
  useSubPageBreadcrumb(t("persona.sidebar.general"))

  return (
    <div className={`w-full px-2 md:px-6 py-2 md:py-8 flex flex-col gap-4${chrome === "bento" ? "" : " max-w-2xl mx-auto"}`}>
      {/* General Info */}
      <SectionCard variant="blue">
        <SectionCard.Header
          icon={IconSettings}
          title={t("forms.common.generalInfo")}
          description={t("forms.persona.generalDesc")}
        />
        <SectionCard.Content>
          <FormField
            control={form.control}
            name="name"
            rules={{ required: t("forms.persona.nameRequired") }}
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.common.name")}</FormLabel>
                <FormControl>
                  <Input
                    {...field}
                    placeholder={t("forms.common.name")}
                    type="text"
                    className="w-full"
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="description"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.common.description")}</FormLabel>
                <FormControl>
                  <Textarea
                    {...field}
                    value={field.value ?? ""}
                    rows={2}
                    maxLength={500}
                    placeholder={t("forms.persona.descriptionPlaceholder")}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="personaKind"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.persona.kind")}</FormLabel>
                <div className="text-muted-foreground text-sm font-normal mt-1">
                  {t("forms.persona.kindDesc")}
                </div>
                <FormControl>
                  <Select
                    value={field.value ?? "SPEAKER"}
                    onValueChange={(v) => field.onChange(v)}
                  >
                    <SelectTrigger className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {PERSONA_KINDS.map((kind) => (
                        <SelectItem key={kind} value={kind}>
                          {t(`persona.kinds.${kind}`)}
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

      {/* Status */}
      <SectionCard variant="slate">
        <SectionCard.Header
          icon={IconToggleLeft}
          title={t("forms.common.status")}
          description={t("forms.persona.statusDesc")}
        />
        <SectionCard.Content>
          <FormField
            control={form.control}
            name="enabled"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>
                    {t("forms.common.enabled")}
                  </FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description className="text-sm font-normal mt-1">
                    {t("forms.persona.enabledDesc")}
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <GradientSwitch
                      checked={Number(field.value) === 1}
                      onCheckedChange={(checked) => field.onChange(checked ? 1 : 0)}
                    />
                  </FormControl>
                </FormItemTwoColumns.Right>
                <FormMessage />
              </FormItemTwoColumns>
            )}
          />
        </SectionCard.Content>
      </SectionCard>

      {/* Danger zone — opens the shared delete dialog rendered in the sidebar. */}
      {!isNew && (
        <SectionCard variant="rose">
          <SectionCard.Header
            icon={IconAlertTriangle}
            title={t("forms.persona.dangerZoneTitle")}
            description={t("forms.persona.dangerZoneDesc")}
          />
          <SectionCard.Content>
            <Button
              type="button"
              variant="destructive"
              onClick={() => setDeleteOpen(true)}
              className="w-full sm:w-auto"
            >
              <IconTrash className="size-4" />
              {t("forms.persona.deleteAction")}
            </Button>
          </SectionCard.Content>
        </SectionCard>
      )}
    </div>
  )
}
