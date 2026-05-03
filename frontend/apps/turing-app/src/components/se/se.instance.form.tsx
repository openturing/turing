"use client"
import { ROUTES } from "@/app/routes.const"
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form"
import {
  Input
} from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import {
  useCreateSeInstance,
  useUpdateSeInstance,
} from "@/api/queries/se-instance.queries"
import type { TurSEInstance } from "@/models/se/se-instance.model.ts"
import type { TurSEVendor } from "@/models/se/se-vendor.model.ts"
import { TurSEVendorService } from "@/services/se/se.service"
import { TurFeaturesService } from "@/services/system/features.service"
import { useEffect, useState } from "react"
import { useTranslation } from "react-i18next"
import {
  useForm, useWatch
} from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { IconDeviceFloppy, IconLock, IconPlug, IconSettings, IconX } from "@tabler/icons-react"
import { GradientButton } from "../ui/gradient-button"
import { StickyPageHeader } from "../sticky-page-header"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { IconPicker } from "../ui/icon-picker"
import { SmartDescription } from "../ui/smart-description"
import { SectionCard } from "../ui/section-card"
const turSEVendorService = new TurSEVendorService();
const turFeaturesService = new TurFeaturesService();

const VENDOR_DEFAULT_URLS: Record<string, string> = {
  SOLR: "http://localhost:8983/solr",
  LUCENE: "/data/turing/lucene",
  ES: "http://localhost:9200",
};
interface Props {
  value: TurSEInstance;
  isNew: boolean;
}

export const SEInstanceForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const form = useForm<TurSEInstance>({
    defaultValues: value
  });
  const { control } = form;
  const [vendors, setVendors] = useState<TurSEVendor[]>([]);
  const [readOnly, setReadOnly] = useState<boolean>(false);
  const navigate = useNavigate()
  const selectedVendorId = useWatch({ control, name: "turSEVendor.id" });
  const createMutation = useCreateSeInstance();
  const updateMutation = useUpdateSeInstance();

  useEffect(() => {
    form.reset(value);
  }, [value])

  useEffect(() => {
    turSEVendorService.query().then(setVendors);
    turFeaturesService.getFeatures()
      .then((f) => setReadOnly(f.seInstanceReadOnly))
      .catch(() => setReadOnly(false));
  }, [])

  useEffect(() => {
    if (!isNew) return;
    const defaultUrl = VENDOR_DEFAULT_URLS[selectedVendorId];
    if (defaultUrl) form.setValue("endpointUrl", defaultUrl);
  }, [selectedVendorId])

  async function onSubmit(seInstance: TurSEInstance) {
    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(seInstance);
        if (result) {
          toast.success(t("forms.common.saved", { name: seInstance.title, feature: t("se.title") }));
          navigate(ROUTES.SE_INSTANCE);
        } else {
          toast.error(t("forms.common.notSaved", { name: seInstance.title, feature: t("se.title") }));
        }
      }
      else {
        const result = await updateMutation.mutateAsync(seInstance);
        if (result) {
          toast.success(t("forms.common.updated", { name: seInstance.title, feature: t("se.title") }));
        } else {
          toast.error(t("forms.common.notUpdated", { name: seInstance.title, feature: t("se.title") }));
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
        <StickyPageHeader>
          <StickyPageHeader.Title
            icon={IconSettings}
            feature={t("se.detail.title")}
            description={t("se.detail.description")}
          />
          <StickyPageHeader.Actions>
            {!readOnly && (
              <GradientButton type="submit" size="sm">
                <IconDeviceFloppy className="size-4" />
                {t("forms.formActions.saveChanges")}
              </GradientButton>
            )}
            <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(ROUTES.SE_INSTANCE)}>
              <IconX className="size-4" />
              {readOnly ? t("forms.formActions.back", { defaultValue: "Back" }) : t("forms.formActions.cancel")}
            </GradientButton>
          </StickyPageHeader.Actions>
        </StickyPageHeader>
        {readOnly && (
          <div className="flex items-center gap-2 rounded-lg border border-amber-300/60 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-600/40 dark:bg-amber-950/30 dark:text-amber-200">
            <IconLock className="size-4 shrink-0" />
            <span>
              {t("se.readOnlyNotice", {
                defaultValue:
                  "Solr is configured via turing.solr.endpoint. The search engine catalog is read-only.",
              })}
            </span>
          </div>
        )}
                {/* General Section */}
                <SectionCard variant="blue">
                  <SectionCard.Header icon={IconSettings} title={t("forms.common.generalInfo")} description={t("forms.se.generalDesc")} />
                  <SectionCard.Content>
                    <FormField
                      control={control}
                      name="title"
                      rules={{ required: t("forms.se.titleRequired") }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("forms.common.title")}</FormLabel>
                          <FormDescription>
                            {t("forms.se.titleDesc")}
                          </FormDescription>
                          <FormControl>
                            <Input
                              {...field}
                              placeholder={t("forms.se.titlePlaceholder")}
                              type="text"
                              readOnly={readOnly}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={control}
                      name="description"
                      render={({ field }) => (
                        <FormItem>
                          <FormControl>
                            <SmartDescription
                              value={field.value}
                              onChange={field.onChange}
                              placeholder={t("forms.se.descriptionPlaceholder")}
                              maxLength={500}
                              title={form.watch("title")}
                              entityType={t("se.title")}
                              enableMetaPrompt
                            >
                              <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                              <SmartDescription.Description>
                                {t("forms.se.descriptionDesc")}
                              </SmartDescription.Description>
                            </SmartDescription>
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={control}
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
                  </SectionCard.Content>
                </SectionCard>

                {/* Connection Section */}
                <SectionCard variant="violet">
                  <SectionCard.Header icon={IconPlug} title={t("forms.se.connectionSettings")} description={t("forms.se.connectionSettingsDesc")} />
                  <SectionCard.Content>
                    <FormField
                      control={control}
                      name="turSEVendor.id"
                      rules={{ required: t("forms.se.vendorRequired") }}
                      render={({ field }) => (
                        <FormItemTwoColumns>
                          <FormItemTwoColumns.Left>
                            <FormItemTwoColumns.Label>{t("forms.common.vendor")}</FormItemTwoColumns.Label>
                            <FormItemTwoColumns.Description>
                              {t("forms.se.vendorDesc")}
                            </FormItemTwoColumns.Description>
                          </FormItemTwoColumns.Left>
                          <FormItemTwoColumns.Right>
                            <FormControl>
                              <Select onValueChange={field.onChange} value={field.value} disabled={readOnly}>
                                <SelectTrigger className="w-full">
                                  <SelectValue placeholder={t("forms.common.selectVendor")} />
                                </SelectTrigger>
                                <SelectContent>
                                  {vendors.map((vendor) => (
                                    <SelectItem key={vendor.id} value={vendor.id}>{vendor.title}</SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                            </FormControl>
                          </FormItemTwoColumns.Right>
                          <FormMessage />
                        </FormItemTwoColumns>
                      )}
                    />
                    <FormField
                      control={control}
                      name="endpointUrl"
                      rules={{ required: t("forms.se.endpointRequired") }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("forms.common.endpointUrl")}</FormLabel>
                          <FormDescription>
                            {t("forms.se.endpointDesc")}
                          </FormDescription>
                          <FormControl>
                            <Input
                              placeholder={t("forms.se.endpointPlaceholder")}
                              type="url"
                              {...field}
                              readOnly={readOnly}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </SectionCard.Content>
                </SectionCard>

        </form>
    </Form>
  )
}

