import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { FormActions } from "@/components/ui/form-actions";
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
import {
  IconChevronDown,
  IconHash,
  IconLink,
  IconSettings,
} from "@tabler/icons-react";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { toast } from "sonner";
import { GradientSwitch } from "../ui/gradient-switch";
import { Stepper } from "../ui/stepper";
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

const MODE_LABELS: Record<string, string> = {
  INDEXING: "Index",
  DEINDEXING: "Deindex",
  PUBLISHING: "Publish",
  UNPUBLISHING: "Unpublish",
};

function AdvancedSettings({ form }: Readonly<{ form: ReturnType<typeof useForm<IndexingManagerFormValues>> }>) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);

  return (
    <div className="mt-6 rounded-lg border border-dashed border-muted-foreground/30">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex w-full items-center gap-2 px-4 py-3 text-sm text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
      >
        <IconSettings className="h-4 w-4" />
        <span className="font-medium">{t("forms.integrationManager.advancedSettings")}</span>
        <span className="text-xs text-muted-foreground/60 ml-1">{t("forms.integrationManager.optional")}</span>
        <IconChevronDown className={`ml-auto h-4 w-4 transition-transform duration-200 ${open ? "rotate-180" : ""}`} />
      </button>
      {open && (
        <div className="px-4 pb-4">
          <FormField
            control={form.control}
            name="recursive"
            render={({ field }) => (
              <FormItem>
                <div className="flex items-center justify-between max-w-md rounded-lg border bg-muted/30 p-4">
                  <div>
                    <FormLabel className="text-sm font-semibold">{t("forms.integrationManager.recursive")}</FormLabel>
                    <FormDescription className="mt-0.5">
                      {t("forms.integrationManager.recursiveDesc")}
                    </FormDescription>
                  </div>
                  <FormControl>
                    <GradientSwitch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                    />
                  </FormControl>
                </div>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
      )}
    </div>
  );
}

export const IntegrationIndexingManagerForm: React.FC<IntegrationIndexingManagerFormProps> = ({
  integrationId,
  mode,
  sources,
}) => {
  const { t } = useTranslation();
  const [submitting, setSubmitting] = useState(false);
  const turIntegrationIndexingManagerService = useMemo(
    () => new TurIntegrationIndexingManagerService(integrationId),
    [integrationId],
  );

  const form = useForm<IndexingManagerFormValues>({
    defaultValues: {
      source: "",
      attribute: undefined,
      values: [""],
      recursive: false,
    },
  });

  const watchSource = form.watch("source");
  const selectedAttribute = form.watch("attribute");
  const watchValues = form.watch("values");

  const step1Done = !!watchSource;
  const step2Done = !!selectedAttribute;
  const step3Done = Array.isArray(watchValues) && watchValues.some(
    (v: any) => (typeof v === "object" && v !== null && "value" in v ? v.value : v)?.toString().trim(),
  );

  async function onSubmit(data: IndexingManagerFormValues) {
    try {
      if (!data.attribute) {
        toast.error(t("forms.integrationManager.selectAttribute"));
        return;
      }

      setSubmitting(true);

      const isRecursive = data.attribute === "id" && data.recursive;

      const payload: TurIntegrationIndexingManager = {
        attribute: data.attribute.toUpperCase() as "ID" | "URL",
        paths: data.values.map((v: any) => typeof v === "object" && v !== null && "value" in v ? v.value : v),
        event: mode,
        ...(isRecursive && { recursive: true }),
      };

      await turIntegrationIndexingManagerService.submit(data.source, payload);
      toast.success(t("forms.integrationManager.requestSuccess", { action: MODE_LABELS[mode] }));

      form.reset({
        source: "",
        attribute: undefined,
        values: [""],
        recursive: false,
      });
    } catch {
      toast.error(t("forms.integrationManager.requestFailed"));
    } finally {
      setSubmitting(false);
    }
  }

  const actionLabel = MODE_LABELS[mode] ?? mode;

  return (
    <Form {...form}>
      <form
        onSubmit={form.handleSubmit(onSubmit)}
        className="space-y-0"
        autoComplete="off"
      >
        <Stepper completedSteps={[step1Done, step2Done, step3Done]}>
          {/* Step 1: Source */}
          <Stepper.Step index={0}>
            <FormField
              control={form.control}
              name="source"
              rules={{ required: true }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-base font-semibold">{t("forms.integrationManager.contentSource")}</FormLabel>
                  <FormDescription>
                    {t("forms.integrationManager.contentSourceDesc")}
                  </FormDescription>
                  <FormControl>
                    <Select value={field.value || ""} onValueChange={field.onChange}>
                      <SelectTrigger className="w-full max-w-md mt-2">
                        <SelectValue placeholder={t("forms.integrationManager.selectSource")} />
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
                  <FormMessage />
                </FormItem>
              )}
            />
          </Stepper.Step>

          {/* Step 2: Attribute */}
          <Stepper.Step index={1}>
            <FormField
              control={form.control}
              name="attribute"
              rules={{ required: true }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="text-base font-semibold">{t("forms.integrationManager.matchBy")}</FormLabel>
                  <FormDescription>
                    {t("forms.integrationManager.matchByDesc")}
                  </FormDescription>
                  <div className="grid grid-cols-2 gap-3 max-w-md mt-2">
                    <button
                      type="button"
                      onClick={() => field.onChange("id")}
                      className={[
                        "flex items-center gap-3 rounded-lg border p-3 text-left transition-all cursor-pointer",
                        field.value === "id"
                          ? "border-primary bg-primary/5 ring-2 ring-primary/20"
                          : "border-border hover:border-muted-foreground/30 hover:bg-accent/50",
                      ].join(" ")}
                    >
                      <IconHash className={`h-5 w-5 ${field.value === "id" ? "text-primary" : "text-muted-foreground"}`} />
                      <div>
                        <div className="text-sm font-medium">{t("forms.integrationManager.identifier")}</div>
                        <div className="text-xs text-muted-foreground">{t("forms.integrationManager.byId")}</div>
                      </div>
                    </button>
                    <button
                      type="button"
                      onClick={() => field.onChange("url")}
                      className={[
                        "flex items-center gap-3 rounded-lg border p-3 text-left transition-all cursor-pointer",
                        field.value === "url"
                          ? "border-primary bg-primary/5 ring-2 ring-primary/20"
                          : "border-border hover:border-muted-foreground/30 hover:bg-accent/50",
                      ].join(" ")}
                    >
                      <IconLink className={`h-5 w-5 ${field.value === "url" ? "text-primary" : "text-muted-foreground"}`} />
                      <div>
                        <div className="text-sm font-medium">URL</div>
                        <div className="text-xs text-muted-foreground">{t("forms.integrationManager.byUrl")}</div>
                      </div>
                    </button>
                  </div>
                  <FormMessage />
                </FormItem>
              )}
            />
          </Stepper.Step>

          {/* Step 3: Values */}
          <Stepper.Step index={2}>
            <FormField
              control={form.control}
              name="values"
              rules={{ required: true }}
              render={() => (
                <FormItem>
                  <FormLabel className="text-base font-semibold">{t("forms.integrationManager.values")}</FormLabel>
                  <FormDescription>
                    Enter the {selectedAttribute === "url" ? "URLs" : "identifiers"} of the items to {actionLabel.toLowerCase()}.
                  </FormDescription>
                  <div className="mt-2">
                    <FormControl>
                      <DynamicIndexingRuleFields<IndexingManagerFormValues>
                        control={form.control}
                        register={form.register}
                        fieldName="values"
                      />
                    </FormControl>
                  </div>
                  <FormMessage />
                </FormItem>
              )}
            />
          </Stepper.Step>

          <Stepper.Completion />
        </Stepper>

        {/* Advanced Settings — separate from the stepper, only for ID */}
        {selectedAttribute === "id" && (
          <AdvancedSettings form={form} />
        )}

        {/* Footer */}
        <FormActions>
          <FormActions.Cancel onClick={() => form.reset()}>{t("forms.common.reset")}</FormActions.Cancel>
          <FormActions.Submit loading={submitting}>{actionLabel}</FormActions.Submit>
        </FormActions>
      </form>
    </Form>
  );
};
