"use client"

import { useCallback, useEffect, useMemo, useRef, useState } from "react"
import { useForm } from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"

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
import { Input } from "@/components/ui/input"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import type { TurIntegrationIndexingRule } from "@/models/integration/integration-indexing-rule.model"
import type { TurSNSiteField } from "@/models/sn/sn-site-field.model"
import type { TurSNSite } from "@/models/sn/sn-site.model"
import { TurIntegrationIndexingRuleService } from "@/services/integration/integration-indexing-rule.service"
import { TurSNFieldService } from "@/services/sn/sn.field.service"
import { TurSNSiteService } from "@/services/sn/sn.service"
import { IconFileText, IconMap, IconGavel, IconList } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { FormActions } from "../ui/form-actions"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { SectionCard } from "../ui/section-card"
import { DynamicIndexingRuleFields } from "./dynamic.indexing.rule.field"
// Constants
const RULE_TYPES = [
  { value: "IGNORE", label: "Ignore" }
] as const;

// Props interface with descriptive naming
interface IntegrationIndexingRulesFormProps {
  value: TurIntegrationIndexingRule;
  integrationId: string;
  isNew: boolean;
}

export const IntegrationIndexingRulesForm: React.FC<IntegrationIndexingRulesFormProps> = ({
  value,
  integrationId,
  isNew
}) => {
  const { t } = useTranslation();
  console.log("isNew", isNew);
  // Services - memoized to prevent recreation on each render
  const turSNSiteService = useMemo(() => new TurSNSiteService(), []);
  const turSNFieldService = useMemo(() => new TurSNFieldService(), []);
  const turIntegrationIndexingRuleService = useMemo(
    () => new TurIntegrationIndexingRuleService(integrationId),
    [integrationId]
  );

  // Form setup with default values
  const form = useForm<TurIntegrationIndexingRule>({
    defaultValues: value
  });
  const { control, register, setValue, watch } = form;

  // State
  const [turSNSites, setTurSNSites] = useState<TurSNSite[]>([]);
  const [turSNSiteFields, setTurSNSiteFields] = useState<TurSNSiteField[]>([]);
  const [isLoadingFields, setIsLoadingFields] = useState(false);

  // Ref to track pending attribute value that needs to be set after fields load
  const pendingAttributeRef = useRef<string | null>(value.attribute || null);

  // Navigation
  const navigate = useNavigate();
  // Watch selected source for dependent field loading
  const selectedSource = watch("source");

  // Find selected site - memoized for performance
  const selectedSite = useMemo(
    () => turSNSites.find((site) => site.name === selectedSource),
    [turSNSites, selectedSource]
  );

  // Load initial data (sites only)
  useEffect(() => {
    const loadSites = async () => {
      try {
        const sites = await turSNSiteService.query();
        setTurSNSites(sites);
      } catch (error) {
        console.error("Failed to load sites:", error);
        toast.error(t("forms.integrationRules.siteLoadFailed"));
      }
    };

    loadSites();
  }, [turSNSiteService]);

  // Reset form when value changes
  useEffect(() => {
    form.reset(value);
    // Store the attribute to be set after fields are loaded
    pendingAttributeRef.current = value.attribute || null;
  }, [form, value]);

  // Load fields when site selection changes
  useEffect(() => {
    const loadFields = async () => {
      if (!selectedSite?.id) {
        setTurSNSiteFields([]);
        return;
      }

      setIsLoadingFields(true);
      try {
        const fields = await turSNFieldService.query(selectedSite.id);
        setTurSNSiteFields(fields);
      } catch (error) {
        console.error("Failed to load fields:", error);
        toast.error(t("forms.integrationRules.fieldLoadFailed"));
      } finally {
        setIsLoadingFields(false);
      }
    };

    loadFields();
  }, [selectedSite?.id, turSNFieldService]);

  // Set attribute value after fields are loaded
  useEffect(() => {
    if (turSNSiteFields.length > 0 && pendingAttributeRef.current) {
      const attributeExists = turSNSiteFields.some(
        (field) => field.name === pendingAttributeRef.current
      );
      if (attributeExists) {
        setValue("attribute", pendingAttributeRef.current);
      }
      pendingAttributeRef.current = null;
    }
  }, [turSNSiteFields, setValue]);

  // Form submission handler - memoized with useCallback
  const onSubmit = useCallback(async (data: TurIntegrationIndexingRule) => {
    try {
      if (isNew) {
        const result = await turIntegrationIndexingRuleService.create(data);
        if (result) {
          form.reset(data); // Reset dirty state after successful save
          toast.success(t("forms.integrationRules.created", { name: data.name }));
          navigate(`${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/indexing-rule`);
        } else {
          toast.error(t("forms.integrationRules.createFailed"));
        }
      } else {
        const result = await turIntegrationIndexingRuleService.update(data);
        if (result) {
          form.reset(data); // Reset dirty state after successful save
          toast.success(t("forms.integrationRules.updated", { name: data.name }));
        } else {
          toast.error(t("forms.integrationRules.updateFailed"));
        }
      }
    } catch (error) {
      console.error("Form submission error:", error);
      toast.error(t("forms.integrationRules.saveFailed"));
    }
  }, [isNew, turIntegrationIndexingRuleService, navigate, form]);

  // Check if attribute field should be disabled
  const isAttributeFieldDisabled = !selectedSource || isLoadingFields;

  return (
    <div className="px-6 py-8">
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <div className="space-y-4">
            {/* Section: Basic Info */}
            <SectionCard variant="blue">
              <SectionCard.Header icon={IconFileText} title={t("forms.integrationRules.ruleDetails")} description={t("forms.integrationRules.ruleDetailsDesc")} />
              <SectionCard.Content>
                {/* Rule Name */}
                <FormField
                  control={control}
                  name="name"
                  rules={{ required: t("forms.integrationRules.ruleNameRequired") }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.integrationRules.ruleName")}</FormLabel>
                      <FormDescription>
                        {t("forms.integrationRules.ruleNameDesc")}
                      </FormDescription>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder={t("forms.integrationRules.ruleNamePlaceholder")}
                          type="text"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                {/* Rule Description */}
                <FormField
                  control={control}
                  name="description"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>{t("forms.integrationRules.ruleDescLabel")}</FormLabel>
                      <FormDescription>
                        {t("forms.integrationRules.ruleDescDesc")}
                      </FormDescription>
                      <FormControl>
                        <Textarea
                          {...field}
                          placeholder={t("forms.integrationRules.ruleDescPlaceholder")}
                          className="resize-none"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </SectionCard.Content>
            </SectionCard>

            {/* Section: Site Settings */}
            <SectionCard variant="violet">
              <SectionCard.Header icon={IconMap} title={t("forms.integrationRules.whereApply")} description={t("forms.integrationRules.whereApplyDesc")} />
              <SectionCard.Content>
                {/* Semantic Navigation Site */}
                <FormField
                  control={control}
                  name="source"
                  rules={{ required: t("forms.integrationRules.siteRequired") }}
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.integrationRules.chooseSite")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.integrationRules.chooseSiteDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value}>
                            <SelectTrigger className="w-full">
                              <SelectValue placeholder={t("forms.integrationRules.selectSite")} />
                            </SelectTrigger>
                            <SelectContent>
                              {turSNSites.map((site) => (
                                <SelectItem key={site.id} value={site.name}>
                                  {site.name}
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

                {/* Target Attribute/Field */}
                <FormField
                  control={control}
                  name="attribute"
                  rules={{ required: t("forms.integrationRules.fieldRequired") }}
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.integrationRules.pickField")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.integrationRules.pickFieldDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select
                            onValueChange={field.onChange}
                            value={field.value}
                            disabled={isAttributeFieldDisabled}
                          >
                            <SelectTrigger className="w-full">
                              <SelectValue
                                placeholder={isLoadingFields ? t("forms.integrationRules.loadingFields") : t("forms.integrationRules.selectField")}
                              />
                            </SelectTrigger>
                            <SelectContent>
                              {turSNSiteFields.map((siteField) => (
                                <SelectItem key={siteField.id} value={siteField.name}>
                                  {siteField.name}
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

            {/* Section: Rule Action */}
            <SectionCard variant="emerald">
              <SectionCard.Header icon={IconGavel} title={t("forms.integrationRules.whatHappens")} description={t("forms.integrationRules.whatHappensDesc")} />
              <SectionCard.Content>
                {/* Action Type */}
                <FormField
                  control={control}
                  name="ruleType"
                  rules={{ required: t("forms.integrationRules.actionRequired") }}
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>{t("forms.integrationRules.actionLabel")}</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          {t("forms.integrationRules.actionDesc")}
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value}>
                            <SelectTrigger className="w-full">
                              <SelectValue placeholder={t("forms.integrationRules.chooseAction")} />
                            </SelectTrigger>
                            <SelectContent>
                              {RULE_TYPES.map((ruleType: typeof RULE_TYPES[number]) => (
                                <SelectItem key={ruleType.value} value={ruleType.value}>
                                  {ruleType.label}
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

            {/* Section: Matching Values */}
            <SectionCard variant="amber">
              <SectionCard.Header icon={IconList} title={t("forms.integrationRules.whichValues")} description={t("forms.integrationRules.whichValuesDesc")} />
              <SectionCard.Content>
                <FormItem>
                  <FormLabel>{t("forms.integrationRules.valuesToMatch")}</FormLabel>
                  <FormDescription>
                    {t("forms.integrationRules.valuesToMatchDesc")}
                  </FormDescription>
                  <FormControl>
                    <DynamicIndexingRuleFields
                      fieldName="values"
                      control={control}
                      register={register}
                    />
                  </FormControl>
                </FormItem>
              </SectionCard.Content>
            </SectionCard>
          </div>

          {/* Action Footer */}
          <FormActions className="mt-6">
            <FormActions.Cancel onClick={() => navigate(`${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/indexing-rule`)} />
            <FormActions.Submit />
          </FormActions>
        </form>
      </Form>
    </div>
  );
}

