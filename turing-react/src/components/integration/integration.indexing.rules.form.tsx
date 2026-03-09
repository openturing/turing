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
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "../ui/accordion"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientButton } from "../ui/gradient-button"
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
        toast.error("Failed to load Semantic Navigation sites.");
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
        toast.error("Failed to load site fields.");
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
          toast.success(`The "${data.name}" Integration Indexing Rule was created successfully.`);
          navigate(`${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/indexing-rule`);
        } else {
          toast.error("Failed to create the rule. Please try again.");
        }
      } else {
        const result = await turIntegrationIndexingRuleService.update(data);
        if (result) {
          form.reset(data); // Reset dirty state after successful save
          toast.success(`The "${data.name}" Integration Indexing Rule was updated successfully.`);
        } else {
          toast.error("Failed to update the rule. Please try again.");
        }
      }
    } catch (error) {
      console.error("Form submission error:", error);
      toast.error("Failed to save the rule. Please try again.");
    }
  }, [isNew, turIntegrationIndexingRuleService, navigate, form]);

  // Check if attribute field should be disabled
  const isAttributeFieldDisabled = !selectedSource || isLoadingFields;

  return (
    <div className="px-6 py-8">
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <Accordion
            type="multiple"
            defaultValue={[
              "basic-info",
              "site-settings",
              "rule-action",
              "matching-values"
            ]}
            className="space-y-6"
          >
            {/* Section: Basic Info */}
            <AccordionItem value="basic-info" className="border rounded-lg px-6">
              <AccordionTrigger className="hover:no-underline">
                <div className="flex items-center gap-2">
                  <span className="text-lg font-semibold text-foreground">
                    Rule Details
                  </span>
                </div>
              </AccordionTrigger>
              <AccordionContent className="space-y-6 pt-4">
                {/* Rule Name */}
                <FormField
                  control={control}
                  name="name"
                  rules={{ required: "Please give your rule a name." }}
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Rule Name</FormLabel>
                      <FormDescription>
                        Give your rule a short, memorable name so you and your team can easily recognize it later.
                      </FormDescription>
                      <FormControl>
                        <Input
                          {...field}
                          placeholder="e.g., Skip Draft Pages"
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
                      <FormLabel>What does this rule do?</FormLabel>
                      <FormDescription>
                        Briefly describe when or why this rule should be used. This helps everyone understand its purpose.
                      </FormDescription>
                      <FormControl>
                        <Textarea
                          {...field}
                          placeholder="e.g., Prevents draft pages from being indexed"
                          className="resize-none"
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </AccordionContent>
            </AccordionItem>

            {/* Section: Site Settings */}
            <AccordionItem value="site-settings" className="border rounded-lg px-6">
              <AccordionTrigger className="hover:no-underline">
                <div className="flex items-center gap-2">
                  <span className="text-lg font-semibold text-foreground">
                    Where does this rule apply?
                  </span>
                </div>
              </AccordionTrigger>
              <AccordionContent className="space-y-6 pt-4">
                {/* Semantic Navigation Site */}
                <FormField
                  control={control}
                  name="source"
                  rules={{ required: "Please select a site." }}
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>Choose a Site</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          Select the Semantic Navigation site where this rule should be active.
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value}>
                            <SelectTrigger className="w-full">
                              <SelectValue placeholder="Select a site..." />
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
                  rules={{ required: "Please select a field." }}
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>Pick a Field</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          Choose the content field this rule should check. Only items with matching values will be affected.
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
                                placeholder={isLoadingFields ? "Loading fields..." : "Select a field..."}
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
              </AccordionContent>
            </AccordionItem>

            {/* Section: Rule Action */}
            <AccordionItem value="rule-action" className="border rounded-lg px-6">
              <AccordionTrigger className="hover:no-underline">
                <div className="flex items-center gap-2">
                  <span className="text-lg font-semibold text-foreground">
                    What happens to matching content?
                  </span>
                </div>
              </AccordionTrigger>
              <AccordionContent className="space-y-6 pt-4">
                {/* Action Type */}
                <FormField
                  control={control}
                  name="ruleType"
                  rules={{ required: "Please choose an action." }}
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>Action for Matching Items</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          Decide what should happen when content matches this rule. For example, "Ignore" will skip these items during indexing.
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <Select onValueChange={field.onChange} value={field.value}>
                            <SelectTrigger className="w-full">
                              <SelectValue placeholder="Choose an action..." />
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
              </AccordionContent>
            </AccordionItem>

            {/* Section: Matching Values */}
            <AccordionItem value="matching-values" className="border rounded-lg px-6">
              <AccordionTrigger className="hover:no-underline">
                <div className="flex items-center gap-2">
                  <span className="text-lg font-semibold text-foreground">
                    Which values trigger this rule?
                  </span>
                </div>
              </AccordionTrigger>
              <AccordionContent className="space-y-6 pt-4">
                <FormItem>
                  <FormLabel>Values to Match</FormLabel>
                  <FormDescription>
                    Add one or more values. If the selected field matches any of these, the rule will apply.
                  </FormDescription>
                  <FormControl>
                    <DynamicIndexingRuleFields
                      fieldName="values"
                      control={control}
                      register={register}
                    />
                  </FormControl>
                </FormItem>
              </AccordionContent>
            </AccordionItem>
          </Accordion>

          {/* Action Footer */}
          <div className="flex justify-end gap-4 mt-8">
            <GradientButton
              type="button"
              variant="outline"
              onClick={() => navigate(`${ROUTES.INTEGRATION_INSTANCE}/${integrationId}/indexing-rule`)}
            >
              Cancel
            </GradientButton>
            <GradientButton type="submit">
              Save Changes
            </GradientButton>
          </div>
        </form>
      </Form>
    </div>
  );
}

