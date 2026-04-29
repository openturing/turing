"use client"
import { ROUTES } from "@/app/routes.const"
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
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
import type { CustomToolParameter, CustomToolPrimitive, TurCustomTool } from "@/models/customtool/custom-tool.model.ts"
import { TurCustomToolService } from "@/services/customtool/custom-tool.service"
import { StreamLanguage } from "@codemirror/language"
import { groovy } from "@codemirror/legacy-modes/mode/groovy"
import { oneDark } from "@codemirror/theme-one-dark"
import { IconBraces, IconCode, IconList, IconSettings, IconToggleLeft } from "@tabler/icons-react"
import CodeMirror from "@uiw/react-codemirror"
import { toast } from "@viglet/viglet-design-system"
import { useEffect, useState } from "react"
import { useFieldArray, useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { DialogDelete } from "../dialog.delete"
import { Button } from "../ui/button"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { IconPicker } from "../ui/icon-picker"
import { SectionCard } from "../ui/section-card"
import { SmartDescription } from "../ui/smart-description"
import { StickySaveBar } from "../ui/sticky-save-bar"

/**
 * @since 2026.2.5
 */

const turCustomToolService = new TurCustomToolService();
const urlBase = ROUTES.CUSTOM_TOOL_INSTANCE;
const PRIMITIVES: CustomToolPrimitive[] = ["string", "integer", "number", "boolean"];

interface FormShape extends Omit<TurCustomTool, "parametersJson"> {
  parameters: CustomToolParameter[];
}

interface Props {
  readonly value: TurCustomTool;
  readonly isNew: boolean;
}

const DEFAULT_SCRIPT = `// Groovy. Args available as variables (e.g. \`name\`) and as the \`args\` map.
// Return any value — it will be coerced to string for the LLM.
return "Hello " + (args.name ?: "world")
`;

function parseParameters(json?: string | null): CustomToolParameter[] {
  if (!json?.trim()) return [];
  try {
    const parsed = JSON.parse(json);
    if (Array.isArray(parsed)) {
      return parsed.filter((p) => p && typeof p.name === "string" && typeof p.type === "string");
    }
  } catch {
    /* ignore */
  }
  return [];
}

export const CustomToolForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const form = useForm<FormShape>({
    defaultValues: {
      ...value,
      description: value.description ?? "",
      llmDescription: value.llmDescription ?? "",
      groovyScript: value.groovyScript ?? DEFAULT_SCRIPT,
      returnType: value.returnType ?? "string",
      parameters: parseParameters(value.parametersJson),
      enabled: value.enabled ?? 1,
    },
  });
  const { fields, append, remove } = useFieldArray({ control: form.control, name: "parameters" });

  useEffect(() => {
    form.reset({
      ...value,
      description: value.description ?? "",
      llmDescription: value.llmDescription ?? "",
      groovyScript: value.groovyScript ?? DEFAULT_SCRIPT,
      returnType: value.returnType ?? "string",
      parameters: parseParameters(value.parametersJson),
      enabled: value.enabled ?? 1,
    });
  }, [value]);

  async function onSubmit(formValue: FormShape) {
    const { parameters, ...rest } = formValue;
    const payload: TurCustomTool = {
      ...rest,
      parametersJson: JSON.stringify(parameters.filter((p) => p.name?.trim())),
    };
    try {
      if (isNew) {
        const result = await turCustomToolService.create(payload);
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.title, feature: t("customTool.title") }));
          navigate(urlBase);
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: t("customTool.title") }));
        }
      } else {
        const result = await turCustomToolService.update(payload);
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.title, feature: t("customTool.title") }));
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.title, feature: t("customTool.title") }));
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  async function onDelete() {
    try {
      if (await turCustomToolService.delete(value)) {
        toast.success(t("forms.common.deleted", { name: value.title, feature: t("customTool.title") }));
        navigate(urlBase);
      } else {
        toast.error(t("forms.common.notDeleted", { name: value.title, feature: t("customTool.title") }));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.notDeleted", { name: value.title, feature: t("customTool.title") }));
    }
    setOpen(false);
  }

  return (
    <div className="w-full px-0 md:px-4 py-4">
      <Card className="mx-auto md:max-w-3xl border-0 shadow-none md:border md:shadow-sm rounded-none md:rounded-xl max-md:**:data-[slot=card-header]:px-2 max-md:**:data-[slot=card-content]:px-2 max-md:**:data-[slot=card-content]:pt-0">
        <CardHeader className="hidden md:grid">
          <CardTitle className="text-2xl">{isNew ? t("customTool.newCustomTool") : t("customTool.title")}</CardTitle>
          <CardAction>
            {!isNew && <DialogDelete feature="Custom Tool" name={value.title} onDelete={onDelete} open={open} setOpen={setOpen} />}
          </CardAction>
          <CardDescription>{t("forms.customTool.settings")}</CardDescription>
        </CardHeader>
        <CardContent>
          <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 max-w-3xl mx-auto py-2 md:py-8">
              <StickySaveBar
                title={form.watch("title") || (isNew ? t("customTool.newCustomTool") : t("customTool.title"))}
                onCancel={() => navigate(urlBase)}
              />

              {/* General Information */}
              <SectionCard variant="blue">
                <SectionCard.Header icon={IconSettings} title={t("forms.common.generalInfo")} description={t("forms.customTool.generalDesc")} />
                <SectionCard.Content>
                  <FormField
                    control={form.control}
                    name="title"
                    rules={{ required: t("forms.customTool.titleRequired") }}
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.common.title")}</FormLabel>
                        <FormDescription>{t("forms.customTool.titleDesc")}</FormDescription>
                        <FormControl>
                          <Input {...field} placeholder={t("forms.customTool.titlePlaceholder")} type="text" />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  <FormField
                    control={form.control}
                    name="description"
                    render={({ field }) => (
                      <FormItem>
                        <FormControl>
                          <SmartDescription
                            value={field.value ?? ""}
                            onChange={field.onChange}
                            placeholder={t("forms.customTool.descriptionPlaceholder")}
                            maxLength={500}
                            title={form.watch("title")}
                            entityType={t("customTool.title")}
                          >
                            <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                            <SmartDescription.Description>{t("forms.customTool.descriptionDesc")}</SmartDescription.Description>
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
                            value={field.value ?? undefined}
                            onChange={(icon) => field.onChange(icon)}
                            onRemove={() => field.onChange(null)}
                            title={form.watch("title")}
                            description={form.watch("description") ?? undefined}
                          >
                            <IconPicker.Label>{t("forms.common.icon")}</IconPicker.Label>
                          </IconPicker>
                        </FormControl>
                      </FormItem>
                    )}
                  />
                </SectionCard.Content>
              </SectionCard>

              {/* LLM Description */}
              <SectionCard variant="violet">
                <SectionCard.Header icon={IconBraces} title={t("forms.customTool.llmDescriptionTitle")} description={t("forms.customTool.llmDescriptionDesc")} />
                <SectionCard.Content>
                  <FormField
                    control={form.control}
                    name="llmDescription"
                    rules={{ required: t("forms.customTool.llmDescriptionRequired") }}
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.customTool.llmDescriptionLabel")}</FormLabel>
                        <FormDescription>{t("forms.customTool.llmDescriptionHint")}</FormDescription>
                        <FormControl>
                          <textarea
                            {...field}
                            rows={4}
                            placeholder={t("forms.customTool.llmDescriptionPlaceholder")}
                            className="w-full rounded-md border bg-background px-3 py-2 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </SectionCard.Content>
              </SectionCard>

              {/* Parameters & Return Type */}
              <SectionCard variant="cyan">
                <SectionCard.Header icon={IconList} title={t("forms.customTool.parametersTitle")} description={t("forms.customTool.parametersDesc")} />
                <SectionCard.Content>
                  <div className="space-y-2">
                    {fields.length === 0 && (
                      <p className="text-xs text-muted-foreground italic">{t("forms.customTool.parametersEmpty")}</p>
                    )}
                    {fields.map((field, index) => (
                      <div
                        key={field.id}
                        className="grid grid-cols-1 gap-2 rounded-md border p-3 sm:grid-cols-[minmax(0,1fr)_10rem_auto] sm:items-end"
                      >
                        <FormField
                          control={form.control}
                          name={`parameters.${index}.name`}
                          rules={{
                            required: t("forms.customTool.parameterNameRequired"),
                            pattern: { value: /^[A-Za-z_][A-Za-z0-9_]*$/, message: t("forms.customTool.parameterNameInvalid") },
                          }}
                          render={({ field: nameField }) => (
                            <FormItem className="mb-0 min-w-0">
                              <FormLabel className="text-xs">{t("forms.customTool.parameterName")}</FormLabel>
                              <FormControl>
                                <Input {...nameField} placeholder="name" className="w-full" />
                              </FormControl>
                              <FormMessage />
                            </FormItem>
                          )}
                        />
                        <FormField
                          control={form.control}
                          name={`parameters.${index}.type`}
                          render={({ field: typeField }) => (
                            <FormItem className="mb-0">
                              <FormLabel className="text-xs">{t("forms.customTool.parameterType")}</FormLabel>
                              <Select value={typeField.value} onValueChange={typeField.onChange}>
                                <FormControl>
                                  <SelectTrigger className="w-full">
                                    <SelectValue />
                                  </SelectTrigger>
                                </FormControl>
                                <SelectContent>
                                  {PRIMITIVES.map((p) => (
                                    <SelectItem key={p} value={p}>
                                      {t(`forms.customTool.primitive.${p}`)}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                            </FormItem>
                          )}
                        />
                        <Button type="button" variant="outline" size="sm" onClick={() => remove(index)}>
                          {t("forms.common.remove")}
                        </Button>
                      </div>
                    ))}
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => append({ name: "", type: "string" })}
                    >
                      {t("forms.customTool.addParameter")}
                    </Button>
                  </div>

                  <div className="w-full flex flex-row justify-between items-center gap-4 pt-4 border-t mt-4">
                    <div className="flex flex-col">
                      <FormLabel>{t("forms.customTool.returnType")}</FormLabel>
                      <FormDescription>{t("forms.customTool.returnTypeDesc")}</FormDescription>
                    </div>
                    <div className="flex-1 max-w-xs">
                      <FormField
                        control={form.control}
                        name="returnType"
                        rules={{ required: true }}
                        render={({ field }) => (
                          <FormItem className="mb-0">
                            <Select value={field.value} onValueChange={field.onChange}>
                              <FormControl>
                                <SelectTrigger className="w-full">
                                  <SelectValue />
                                </SelectTrigger>
                              </FormControl>
                              <SelectContent>
                                {PRIMITIVES.map((p) => (
                                  <SelectItem key={p} value={p}>
                                    {t(`forms.customTool.primitive.${p}`)}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </FormItem>
                        )}
                      />
                    </div>
                  </div>
                </SectionCard.Content>
              </SectionCard>

              {/* Groovy Script */}
              <SectionCard variant="emerald">
                <SectionCard.Header icon={IconCode} title={t("forms.customTool.scriptTitle")} description={t("forms.customTool.scriptDesc")} />
                <SectionCard.Content>
                  <FormField
                    control={form.control}
                    name="groovyScript"
                    rules={{ required: t("forms.customTool.scriptRequired") }}
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.customTool.scriptLabel")}</FormLabel>
                        <FormDescription>{t("forms.customTool.scriptHint")}</FormDescription>
                        <FormControl>
                          <div className="overflow-hidden rounded-md border">
                            <CodeMirror
                              value={field.value ?? ""}
                              height="360px"
                              theme={oneDark}
                              extensions={[StreamLanguage.define(groovy)]}
                              onChange={(val) => field.onChange(val)}
                              basicSetup={{ lineNumbers: true, foldGutter: true, highlightActiveLine: true }}
                            />
                          </div>
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </SectionCard.Content>
              </SectionCard>

              {/* Status */}
              <SectionCard variant="slate">
                <SectionCard.Header icon={IconToggleLeft} title={t("forms.common.status")} description={t("forms.customTool.statusDesc")} />
                <SectionCard.Content>
                  <FormField
                    control={form.control}
                    name="enabled"
                    render={({ field }) => (
                      <FormItemTwoColumns>
                        <FormItemTwoColumns.Left>
                          <FormItemTwoColumns.Label>{t("forms.common.enabled")}</FormItemTwoColumns.Label>
                          <FormItemTwoColumns.Description>{t("forms.customTool.enabledDesc")}</FormItemTwoColumns.Description>
                        </FormItemTwoColumns.Left>
                        <FormItemTwoColumns.Right>
                          <FormControl>
                            <GradientSwitch
                              checked={field.value === 1}
                              onCheckedChange={(checked) => field.onChange(checked ? 1 : 0)}
                            />
                          </FormControl>
                        </FormItemTwoColumns.Right>
                      </FormItemTwoColumns>
                    )}
                  />
                </SectionCard.Content>
              </SectionCard>
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
};
