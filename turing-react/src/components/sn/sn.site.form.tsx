"use client"
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
import type { TurSEInstance } from "@/models/se/se-instance.model"
import type { TurSNSite } from "@/models/sn/sn-site.model.ts"
import { TurSEInstanceService } from "@/services/se/se.service"
import { TurSNSiteService } from "@/services/sn/sn.service"
import { useCallback, useEffect, useState } from "react"
import {
  useForm
} from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { FormActions } from "../ui/form-actions"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { IconPicker } from "../ui/icon-picker"
import { SmartDescription } from "../ui/smart-description"
import { SectionCard } from "../ui/section-card"
import { Skeleton } from "../ui/skeleton"
import { IconSettings, IconSearch } from "@tabler/icons-react"
const turSNSiteService = new TurSNSiteService();
const turSEInstanceService = new TurSEInstanceService();
interface Props {
  value: TurSNSite;
  isNew: boolean;
}
export const SNSiteForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const [isLoading, setIsLoading] = useState(true);
  const [seInstances, setSeInstances] = useState<TurSEInstance[]>([]);
  const form = useForm<TurSNSite>({
    defaultValues: value
  });
  const urlBase = "/admin/sn/instance";
  const navigate = useNavigate()

  const validateNameUnique = useCallback(async (name: string) => {
    if (!name) return true;
    try {
      const exists = await turSNSiteService.nameExists(name, isNew ? undefined : value.id);
      return exists ? t("forms.snSiteForm.nameDuplicate") : true;
    } catch {
      return true;
    }
  }, [isNew, value.id, t]);

  useEffect(() => {
    turSEInstanceService.query().then(setSeInstances);
    form.reset(value);
    setIsLoading(false);
  }, [value])

  async function onSubmit(snSite: TurSNSite) {
    setIsLoading(true);
    try {
      if (isNew) {
        const result = await turSNSiteService.create(snSite);
        setIsLoading(false)
        if (result) {
          toast.success(t("sn.siteSaved", { name: snSite.name }));
          navigate(urlBase);
        } else {
          toast.error(t("sn.siteNotSaved", { name: snSite.name }));
        }
      }
      else {
        const result = await turSNSiteService.update(snSite);
        setIsLoading(false)
        if (result) {
          toast.success(t("sn.siteUpdated", { name: snSite.name }));
        } else {
          toast.error(t("sn.siteNotUpdated", { name: snSite.name }));
        }
      }
    } catch (error: unknown) {
      console.error("Form submission error", error);
      if (error && typeof error === "object" && "response" in error) {
        const axiosError = error as { response?: { status?: number } };
        if (axiosError.response?.status === 409) {
          toast.error(t("forms.snSiteForm.nameDuplicate"));
          form.setError("name", { type: "manual", message: t("forms.snSiteForm.nameDuplicate") });
          setIsLoading(false);
          return;
        }
      }
      toast.error(t("sn.formSubmitFailed"));
      setIsLoading(false);
    }
  }
  return (
    <>
      {
        isLoading ? (
          <div className="flex w-full max-w-xs flex-col gap-7 px-6">
            <div className="flex flex-col gap-3">
              <Skeleton className="h-4 w-20" />
              <Skeleton className="h-8 w-full" />
            </div>
            <div className="flex flex-col gap-3">
              <Skeleton className="h-4 w-24" />
              <Skeleton className="h-8 w-full" />
            </div>
            <Skeleton className="h-8 w-24" />
          </div>
        ) : (
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8 px-6">
                {/* General Information Section */}
                <SectionCard variant="blue">
                  <SectionCard.Header icon={IconSettings} title={t("forms.snSiteForm.generalInfo")} description={t("forms.snSiteForm.generalInfoDesc")} />
                  <SectionCard.Content>
                    {/* Name Field */}
                    <FormField
                      control={form.control}
                      name="name"
                      rules={{
                        required: t("forms.snSiteForm.nameRequired"),
                        pattern: {
                          value: /^[a-zA-Z0-9_-]+$/,
                          message: t("forms.snSiteForm.namePattern"),
                        },
                        validate: {
                          unique: validateNameUnique
                        },
                      }}
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>{t("forms.snSiteForm.nameLabel")}</FormLabel>
                          <FormDescription>
                            {t("forms.snSiteForm.nameDesc")}
                          </FormDescription>
                          <FormControl>
                            <Input {...field} placeholder={t("forms.snSiteForm.namePlaceholder")} type="text" />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />

                    {/* Description Field */}
                    <FormField
                      control={form.control}
                      name="description"
                      rules={{
                        maxLength: {
                          value: 500,
                          message: t("forms.snSiteForm.descMaxLength"),
                        },
                      }}
                      render={({ field }) => (
                        <FormItem>
                          <FormControl>
                            <SmartDescription
                              value={field.value}
                              onChange={field.onChange}
                              placeholder={t("forms.snSiteForm.descPlaceholder")}
                              maxLength={500}
                              title={form.watch("name")}
                              entityType={t("sn.title")}
                            >
                              <SmartDescription.Label>{t("forms.snSiteForm.descLabel")}</SmartDescription.Label>
                              <SmartDescription.Description>
                                {t("forms.snSiteForm.descDesc")}
                              </SmartDescription.Description>
                            </SmartDescription>
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
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
                              title={form.watch("name")}
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

                {/* Search Engine Section */}
                <SectionCard variant="emerald">
                  <SectionCard.Header icon={IconSearch} title={t("forms.snSiteForm.searchEngine")} description={t("forms.snSiteForm.searchEngineDesc")} />
                  <SectionCard.Content>
                    {/* Search Engine Instance (Select) */}
                    <FormField
                      control={form.control}
                      name="turSEInstance.id"
                      rules={{ required: t("forms.snSiteForm.seInstanceRequired") }}
                      render={({ field }) => (
                        <FormItemTwoColumns>
                          <FormItemTwoColumns.Left>
                            <FormItemTwoColumns.Label>{t("forms.snSiteForm.seInstanceLabel")}</FormItemTwoColumns.Label>
                            <FormItemTwoColumns.Description>
                              {t("forms.snSiteForm.seInstanceDesc")}
                            </FormItemTwoColumns.Description>
                          </FormItemTwoColumns.Left>
                          <FormItemTwoColumns.Right>
                            <FormControl>
                              <Select onValueChange={field.onChange} value={field.value}>
                                <SelectTrigger className="w-full">
                                  <SelectValue placeholder={t("globalSettings.choose")} />
                                </SelectTrigger>
                                <SelectContent>
                                  {seInstances.map((seInstance) => (
                                    <SelectItem key={seInstance.id} value={seInstance.id}>
                                      {seInstance.title}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                            </FormControl>
                          </FormItemTwoColumns.Right>
                          <FormMessage />
                        </FormItemTwoColumns>
                      )}
                    />
                  </SectionCard.Content>
                </SectionCard>

              <FormActions>
                <FormActions.Cancel onClick={() => navigate(urlBase)} />
                <FormActions.Submit />
              </FormActions>
            </form>
          </Form>
        )}
    </>
  )
}
