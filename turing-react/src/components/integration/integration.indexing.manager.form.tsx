import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns";
import { GradientButton } from "@/components/ui/gradient-button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import type { TurIntegrationAemSource } from "@/models/integration/integration-aem-source.model";
import type { TurIntegrationIndexingManager } from "@/models/integration/integration-indexing-manager.model";
import { TurIntegrationIndexingManagerService } from "@/services/integration/integration-indexing-manager.service";
import { useMemo } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { GradientSwitch } from "../ui/gradient-switch";
import { DynamicIndexingRuleFields } from "./dynamic.indexing.rule.field";

interface IndexingManagerFormValues {
  source: string;
  attribute?: "id" | "url";
  values: string[];
  recursive: boolean;
}

interface IntegrationIndexingManagerFormProps {
  integrationId: string;
  mode: "PUBLISHING" | "UNPUBLISHING" | "INDEXING" | "DEINDEXING";
  sources?: TurIntegrationAemSource[];
}

export const IntegrationIndexingManagerForm: React.FC<IntegrationIndexingManagerFormProps> = ({
  integrationId,
  mode,
  sources,
}) => {
  const turIntegrationIndexingManagerService = useMemo(
    () => new TurIntegrationIndexingManagerService(integrationId),
    [integrationId]
  );

  const form = useForm<IndexingManagerFormValues>({
    defaultValues: {
      source: "",
      attribute: undefined,
      values: [""],
      recursive: false,
    },
  });

  async function onSubmit(data: IndexingManagerFormValues) {
    try {
      if (!data.attribute) {
        toast.error("Please select a target attribute.");
        return;
      }

      const isRecursive = data.attribute === "id" && data.recursive;

      const payload: TurIntegrationIndexingManager = {
        attribute: data.attribute.toUpperCase() as "ID" | "URL",
        paths: data.values.map((v: any) => typeof v === "object" && v !== null && "value" in v ? v.value : v),
        event: mode,
        ...(isRecursive && { recursive: true }),
      };

      await turIntegrationIndexingManagerService.submit(data.source, payload);
      toast.success(`${mode} requested successfully`);

      form.reset({
        source: "",
        attribute: undefined,
        values: [""],
        recursive: false,
      });
    } catch (error) {
      toast.error("Request failed");
      console.error(error);
    }
  }

  const selectedAttribute = form.watch("attribute");

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="space-y-8"
        autoComplete="off"
      >
        <Accordion
          type="multiple"
          defaultValue={[
            "content-source",
            "target-attribute",
            "matching-values",
            "advanced-options",
          ]}
          className="space-y-6"
        >
          {/* Content Source */}
          <AccordionItem
            value="content-source"
            className="border rounded-lg px-6"
          >
            <AccordionTrigger className="text-lg font-semibold">
              Content Source
            </AccordionTrigger>
            <AccordionContent className="space-y-6">
              <FormField
                control={form.control}
                name="source"
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>Where should we look?</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        Choose the content source you want to manage. This is where your indexing or publishing actions will apply.
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Select
                          value={field.value || ""}
                          onValueChange={field.onChange}
                        >
                          <SelectTrigger className="w-full">
                            <SelectValue placeholder="Pick a source..." />
                          </SelectTrigger>
                          <SelectContent>
                            {sources?.map((s) => (
                              <SelectItem key={s.id} value={s.name}>
                                {s.name}
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

          {/* Target Attribute */}
          <AccordionItem
            value="target-attribute"
            className="border rounded-lg px-6"
          >
            <AccordionTrigger className="text-lg font-semibold">
              Target Attribute
            </AccordionTrigger>
            <AccordionContent className="space-y-6">
              <FormField
                control={form.control}
                name="attribute"
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>How should we match?</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        Select how you want to identify items: by their unique Identifier or by their URL.
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Select
                          value={field.value || ""}
                          onValueChange={field.onChange}
                        >
                          <SelectTrigger className="w-full">
                            <SelectValue placeholder="Choose an attribute..." />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="id">Identifier</SelectItem>
                            <SelectItem value="url">URL</SelectItem>
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

          {/* Matching Values */}
          <AccordionItem
            value="matching-values"
            className="border rounded-lg px-6"
          >
            <AccordionTrigger className="text-lg font-semibold">
              Matching Values
            </AccordionTrigger>
            <AccordionContent className="space-y-6">
              <FormField
                control={form.control}
                name="values"
                rules={{ required: true }}
                render={() => (
                  <FormItem>
                    {/* Label + Description on top */}
                    <FormLabel>What are you targeting?</FormLabel>
                    <FormDescription>
                      Enter one or more values (Identifiers or URLs) to match the items you want to process.
                    </FormDescription>
                    <div className="flex flex-row items-center w-full mt-2">
                      {/* Right: Dynamic Fields */}
                      <div className="w-full flex justify-end">
                        <FormControl>
                          <DynamicIndexingRuleFields
                            control={form.control}
                            register={form.register}
                            fieldName="values"
                          />
                        </FormControl>
                      </div>
                    </div>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </AccordionContent>
          </AccordionItem>

          {/* Advanced Options */}
          {selectedAttribute === "id" && (
            <AccordionItem
              value="advanced-options"
              className="border rounded-lg px-6"
            >
              <AccordionTrigger className="text-lg font-semibold">
                Advanced Options
              </AccordionTrigger>
              <AccordionContent className="space-y-6">
                <FormField
                  control={form.control}
                  name="recursive"
                  render={({ field }) => (
                    <FormItemTwoColumns>
                      <FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Label>Include child items?</FormItemTwoColumns.Label>
                        <FormItemTwoColumns.Description>
                          Turn this on to also apply the action to all child pages and assets.
                        </FormItemTwoColumns.Description>
                      </FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Right>
                        <FormControl>
                          <GradientSwitch
                            checked={field.value}
                            onCheckedChange={field.onChange}
                          />
                        </FormControl>
                      </FormItemTwoColumns.Right>
                      <FormMessage />
                    </FormItemTwoColumns>
                  )}
                />
              </AccordionContent>
            </AccordionItem>
          )}
        </Accordion>

        {/* Action Footer */}
        <div className="flex justify-end gap-2 pt-4">
          <GradientButton type="button" variant="outline" onClick={() => form.reset()}>
            Cancel
          </GradientButton>
          <GradientButton type="submit">{mode.charAt(0) + mode.slice(1).toLowerCase()}</GradientButton>
        </div>
      </form>
    </Form>
  );
};
