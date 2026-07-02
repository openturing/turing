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
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Button } from "@/components/ui/button"
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { GradientSwitch } from "@/components/ui/gradient-switch"
import { IconPicker } from "@/components/ui/icon-picker"
import { SectionCard } from "@/components/ui/section-card"
import { SmartDescription } from "@/components/ui/smart-description"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { IconAlertTriangle, IconSettings, IconToggleLeft, IconTrash } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { useOutletContext } from "react-router-dom"

/**
 * "Geral" section of the LLM instance editor — general info, status toggles and
 * the danger zone. Shares the parent layout's single form instance; the delete
 * confirmation dialog itself lives in the internal sidebar (shared `open`
 * state), so the danger-zone button only flips that state.
 *
 * @since 2026.3.4
 */
export default function LLMInstanceGeneralSection() {
  const { t } = useTranslation();
  const { form, isNew, readOnly, vendors, applyVendorDefaults, setDeleteOpen } =
    useOutletContext<LLMFormContext>();
  useSubPageBreadcrumb(t("llm.sidebar.general"));

  return (
    <div className="w-full max-w-2xl mx-auto px-2 md:px-6 py-2 md:py-8 flex flex-col gap-4">
      {/* General Section */}
      <SectionCard variant="blue">
        <SectionCard.Header icon={IconSettings} title={t("forms.common.generalInfo")} description={t("forms.llm.titleAndVendor")} />
        <SectionCard.Content>
          {/* Title */}
          <div className="w-full">
            <FormField
              control={form.control}
              name="title"
              rules={{ required: t("forms.llm.titleRequired") }}
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.common.title")}</FormLabel>
                  <div className="text-muted-foreground text-sm font-normal mt-1">
                    {t("forms.llm.titleDesc")}
                  </div>
                  <FormControl>
                    <Input {...field} placeholder={t("forms.common.title")} type="text" className="w-full" />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          {/* Vendor (inline row) */}
          <div className="w-full flex flex-row justify-between items-center">
            <div className="flex flex-col">
              <FormLabel>{t("forms.common.vendor")}</FormLabel>
              <div className="text-muted-foreground text-sm font-normal mt-1">
                {t("forms.llm.vendorDesc")}
              </div>
            </div>
            <div className="flex-1 ml-8">
              <FormField
                control={form.control}
                name="turLLMVendor.id"
                rules={{ required: t("forms.llm.vendorRequired") }}
                render={({ field }) => (
                  <FormItem className="w-full">
                    <FormControl>
                      <Select
                        onValueChange={(nextValue) => {
                          field.onChange(nextValue);
                          applyVendorDefaults(nextValue);
                        }}
                        value={field.value}
                      >
                        <SelectTrigger className="w-full">
                          <SelectValue placeholder="Choose..." />
                        </SelectTrigger>
                        <SelectContent>
                          {vendors.map((vendor) => (
                            <SelectItem key={vendor.id} value={vendor.id}>{vendor.title}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
          </div>
          {/* Description */}
          <div className="w-full">
            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormControl>
                    <SmartDescription
                      value={field.value}
                      onChange={field.onChange}
                      placeholder={t("forms.common.description")}
                      className="w-full"
                      rows={2}
                      maxLength={500}
                      title={form.watch("title")}
                      entityType={t("llm.title")}
                      enableMetaPrompt
                    >
                      <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                      <SmartDescription.Description>
                        {t("forms.llm.descriptionDesc")}
                      </SmartDescription.Description>
                    </SmartDescription>
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
          <div className="w-full">
            <FormField
              control={form.control}
              name="icon"
              render={({ field }) => (
                <FormItem>
                  <FormControl>
                    <IconPicker
                      value={field.value}
                      onChange={(icon) => field.onChange(icon)}
                      onRemove={() => field.onChange(null)}
                      title={form.watch("title")}
                      description={form.watch("description")}
                    >
                      <IconPicker.Label>{t("forms.common.icon")}</IconPicker.Label>
                    </IconPicker>
                  </FormControl>
                </FormItem>
              )}
            />
          </div>
        </SectionCard.Content>
      </SectionCard>

      {/* Status Section */}
      <SectionCard variant="slate">
        <SectionCard.Header icon={IconToggleLeft} title={t("forms.common.status")} description={t("forms.llm.statusDesc")} />
        <SectionCard.Content>
          <FormField
            control={form.control}
            name="enabled"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.common.enabled")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description className="text-sm font-normal mt-1">
                    {t("forms.llm.enabledDesc")}
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value === 1}
                      onCheckedChange={(checked) => field.onChange(checked ? 1 : 0)}
                    />
                  </FormControl>
                </FormItemTwoColumns.Right>
                <FormMessage />
              </FormItemTwoColumns>
            )}
          />
          <FormField
            control={form.control}
            name="toolsEnabled"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.llm.toolsEnabled")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description className="text-sm font-normal mt-1">
                    {t("forms.llm.toolsEnabledDesc")}
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value === true}
                      onCheckedChange={(checked) => field.onChange(checked)}
                    />
                  </FormControl>
                </FormItemTwoColumns.Right>
                <FormMessage />
              </FormItemTwoColumns>
            )}
          />
          <FormField
            control={form.control}
            name="fileUploadEnabled"
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.llm.fileUploadEnabled")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description className="text-sm font-normal mt-1">
                    {t("forms.llm.fileUploadEnabledDesc")}
                  </FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value === true}
                      onCheckedChange={(checked) => field.onChange(checked)}
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
      {!isNew && !readOnly && (
        <SectionCard variant="rose">
          <SectionCard.Header
            icon={IconAlertTriangle}
            title={t("forms.llm.dangerZoneTitle")}
            description={t("forms.llm.dangerZoneDesc")}
          />
          <SectionCard.Content>
            <Button
              type="button"
              variant="destructive"
              onClick={() => setDeleteOpen(true)}
              className="w-full sm:w-auto"
            >
              <IconTrash className="size-4" />
              {t("forms.llm.deleteAction")}
            </Button>
          </SectionCard.Content>
        </SectionCard>
      )}
    </div>
  )
}
