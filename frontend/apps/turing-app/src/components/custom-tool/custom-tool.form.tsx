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
import {
  useClearCustomToolDraft,
  useCreateCustomTool,
  useCustomToolDescriptor,
  useCustomToolDraftStatus,
  useDeleteCustomTool,
  usePushCustomToolDraft,
  useUpdateCustomTool,
} from "@/api/queries/custom-tool.queries"
import type { CustomToolParameter, CustomToolPrimitive, TurCustomTool } from "@/models/customtool/custom-tool.model.ts"
import { TurCustomToolService, type ValidateScriptResponse } from "@/services/customtool/custom-tool.service"
import { VibeCodingButton } from "@/components/custom-tool/vibe-coding-button"
import { groovyAutocomplete } from "@/components/custom-tool/groovy-autocomplete"
import { StreamLanguage } from "@codemirror/language"
import { groovy } from "@codemirror/legacy-modes/mode/groovy"
import { oneDark } from "@codemirror/theme-one-dark"
import { IconAlertTriangle, IconBolt, IconBoltOff, IconBraces, IconCheck, IconCode, IconList, IconLoader2, IconPlayerPlay, IconSettings, IconToggleLeft, IconTrash, IconX } from "@tabler/icons-react"
import CodeMirror from "@uiw/react-codemirror"
import { toast } from "@viglet/viglet-design-system"
import { useEffect, useMemo, useState } from "react"
import { useFieldArray, useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { DialogDelete } from "../dialog.delete"
import { Button } from "../ui/button"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { IconPicker } from "../ui/icon-picker"
import { PromptEditor } from "../ui/prompt-editor"
import { SectionCard } from "../ui/section-card"
import { SmartDescription } from "../ui/smart-description"
import { StickySaveBar } from "../ui/sticky-save-bar"

/**
 * @since 2026.2.5
 */

const turCustomToolService = new TurCustomToolService();
const urlBase = ROUTES.CUSTOM_TOOL_INSTANCE;
/** Types acceptable as parameter primitives (LLM-callable args). */
const PARAM_PRIMITIVES: CustomToolPrimitive[] = ["string", "integer", "number", "boolean"];
/** Types acceptable as return type. The "rich" variants (html / markdown /
 *  json) trigger backend wrapping + verbatim relay so the chat UI can
 *  render the script output in a special way. */
const RETURN_PRIMITIVES: CustomToolPrimitive[] = [
  "string", "integer", "number", "boolean", "html", "markdown", "json",
];

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

/**
 * Builds the system message handed to the LLM by the Vibe Coding sheet.
 * Includes the runtime contract (bindings, return coercion, no class wrapper)
 * AND the live tool spec (title, parameters, return type) so the generated
 * code matches the form's current state.
 */
function buildGroovyFieldInstruction(spec: {
  title?: string;
  llmDescription?: string;
  parameters?: CustomToolParameter[];
  returnType?: CustomToolPrimitive;
}): string {
  const params = spec.parameters?.length
    ? spec.parameters.filter((p) => p.name?.trim()).map((p) => `${p.name}: ${p.type}`).join(", ")
    : "(none)";
  return [
    "You are generating Groovy 4 source code that runs as the body of a Spring AI tool callback inside the Turing Enterprise Search platform.",
    "",
    "EXECUTION CONTRACT (cannot be violated):",
    "- The script runs inside a GroovyShell with a Binding — the body is executed directly, NOT inside a class or method wrapper. Do NOT define a class, package, or top-level method.",
    "- Each declared parameter is exposed as a top-level variable with that exact name. Reference the parameter directly (e.g. `name`, `courseName`).",
    "- The full argument map is also available as `args` (Map<String, Object>) — use `args.fieldName ?: defaultValue` to defend against missing values.",
    "- Use top-level `return` to produce the final value. The result is coerced via .toString() before being returned to the LLM, so it must be a single value (string/number/boolean/Map/List/etc).",
    "- Common Java/Groovy stdlib (java.util, java.time, etc.) is available implicitly. Add explicit `import` statements only when strictly necessary.",
    "- Do NOT call System.exit, do NOT spawn threads, do NOT do file/network I/O unless the user explicitly asks for it.",
    "",
    "TOOL SPEC (current form state — match it exactly):",
    `- Tool title: ${spec.title?.trim() || "(unset)"}`,
    `- LLM-facing description: ${spec.llmDescription?.trim() || "(unset)"}`,
    `- Parameters: ${params}`,
    `- Return type: ${spec.returnType || "string"}`,
    "",
    "OUTPUT RULES:",
    "- Return ONLY the Groovy source code.",
    "- Do NOT wrap the output in markdown code fences (no ```groovy, no ```).",
    "- Do NOT include preamble, commentary, headers, or trailing explanation.",
    "- If a current script exists, prefer the smallest change that satisfies the user's instructions.",
    "- The result must compile cleanly with `new GroovyShell().getClassLoader().parseClass(...)`.",
  ].join("\n");
}

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
  const [validating, setValidating] = useState(false);
  const [validation, setValidation] = useState<ValidateScriptResponse | null>(null);

  const createMutation = useCreateCustomTool();
  const updateMutation = useUpdateCustomTool();
  const deleteMutation = useDeleteCustomTool();
  // T40: editor descriptor drives CodeMirror auto-complete for the helper
  // bindings (`http`, `slots`, `turingSearch`, `code`) + `args`. Cached for
  // the session via useCustomToolDescriptor.
  const { data: editorDescriptor } = useCustomToolDescriptor();
  const groovyExtensions = useMemo(() => {
    const base = [StreamLanguage.define(groovy)];
    return editorDescriptor ? [...base, groovyAutocomplete(editorDescriptor)] : base;
  }, [editorDescriptor]);
  // T41: live-preview draft — push the in-editor Groovy as an admin-scoped
  // overlay so the same admin's chat sessions execute the draft for this
  // tool. Disabled for new (unsaved) tools — needs the persisted id.
  const draftToolId = isNew ? undefined : value.id;
  const { data: draftStatus } = useCustomToolDraftStatus(draftToolId);
  const pushDraftMutation = usePushCustomToolDraft(draftToolId);
  const clearDraftMutation = useClearCustomToolDraft(draftToolId);

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

  async function onValidate() {
    if (validating) return;
    const script = form.getValues("groovyScript") ?? "";
    setValidating(true);
    setValidation(null);
    try {
      const result = await turCustomToolService.validate(script);
      setValidation(result);
    } catch (error) {
      console.error("Validate error", error);
      setValidation({ valid: false, error: t("forms.customTool.validateUnreachable") });
    } finally {
      setValidating(false);
    }
  }

  async function onSubmit(formValue: FormShape) {
    const { parameters, ...rest } = formValue;
    const payload: TurCustomTool = {
      ...rest,
      parametersJson: JSON.stringify(parameters.filter((p) => p.name?.trim())),
    };
    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(payload);
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.title, feature: t("customTool.title") }));
          navigate(urlBase);
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: t("customTool.title") }));
        }
      } else {
        const result = await updateMutation.mutateAsync(payload);
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
      if (await deleteMutation.mutateAsync(value)) {
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
                            metaPrompt={form.watch("descriptionMetaPrompt") ?? ""}
                            onMetaPromptChange={(v) =>
                              form.setValue("descriptionMetaPrompt", v, { shouldDirty: true })
                            }
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
                        <FormDescription>
                          {t("forms.customTool.llmDescriptionHint")}
                        </FormDescription>
                        <FormControl>
                          <PromptEditor
                            value={field.value ?? ""}
                            onChange={field.onChange}
                            fieldRef={field.ref}
                            placeholder={t("forms.customTool.llmDescriptionPlaceholder")}
                            rows={4}
                            metaPrompt={{
                              brief: form.watch("llmDescriptionMetaPrompt") ?? "",
                              onBriefChange: (v) =>
                                form.setValue("llmDescriptionMetaPrompt", v, { shouldDirty: true }),
                              fieldInstruction:
                                "This text is the description of a Spring AI tool calling — it is sent to the LLM as the @Tool description so the model can decide WHEN to invoke this tool and WHAT arguments to pass. " +
                                "MUST be written in English regardless of any other instruction. " +
                                "MUST clearly state the tool's purpose, the trigger conditions (when the LLM should call it), the input parameters and their meaning, and the format of the returned value. " +
                                "Be concise but specific; avoid filler. Do not include preamble, quotes, markdown, or commentary — return only the description text.",
                              tone: "violet",
                              triggerLabel: t("forms.customTool.helpWriteLlmDescription"),
                              title: t("forms.customTool.helpWriteLlmDescriptionTitle"),
                              description: t("forms.customTool.helpWriteLlmDescriptionDescription"),
                              placeholder: t("forms.customTool.helpWriteLlmDescriptionPlaceholder"),
                              hint: t("forms.customTool.helpWriteLlmDescriptionHint"),
                              generateLabel: t("forms.customTool.helpWriteLlmDescriptionGenerate"),
                            }}
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
                            pattern: { value: /^[A-Za-z_]\w*$/, message: t("forms.customTool.parameterNameInvalid") },
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
                                  {PARAM_PRIMITIVES.map((p) => (
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
                                {RETURN_PRIMITIVES.map((p) => (
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
                              extensions={groovyExtensions}
                              onChange={(val) => {
                                field.onChange(val);
                                if (validation) setValidation(null);
                              }}
                              basicSetup={{ lineNumbers: true, foldGutter: true, highlightActiveLine: true }}
                            />
                          </div>
                        </FormControl>
                        <div className="flex flex-col gap-2 mt-2 sm:flex-row sm:items-center sm:flex-wrap">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={onValidate}
                            disabled={validating || !field.value?.trim()}
                            className="gap-2 sm:w-auto"
                          >
                            {validating ? (
                              <IconLoader2 className="size-4 animate-spin" />
                            ) : (
                              <IconPlayerPlay className="size-4" />
                            )}
                            {validating
                              ? t("forms.customTool.validateRunning")
                              : t("forms.customTool.validateButton")}
                          </Button>
                          <VibeCodingButton
                            value={field.value ?? ""}
                            onChange={(val) => {
                              field.onChange(val);
                              if (validation) setValidation(null);
                            }}
                            metaPrompt={form.watch("groovyMetaPrompt") ?? ""}
                            onMetaPromptChange={(v) =>
                              form.setValue("groovyMetaPrompt", v, { shouldDirty: true })
                            }
                            fieldInstruction={buildGroovyFieldInstruction({
                              title: form.watch("title"),
                              llmDescription: form.watch("llmDescription"),
                              parameters: form.watch("parameters"),
                              returnType: form.watch("returnType"),
                            })}
                          />
                          {/* T41 — live-preview draft controls. Only available for saved tools (needs an id). */}
                          {draftToolId && (
                            <>
                              <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={() => pushDraftMutation.mutate(field.value ?? "")}
                                disabled={pushDraftMutation.isPending || !field.value?.trim()}
                                className="gap-2 sm:w-auto"
                                title={t("forms.customTool.draftPushHint", {
                                  defaultValue: "Push this script as your live draft — your chat sessions will run it instead of the saved version.",
                                })}
                              >
                                {pushDraftMutation.isPending
                                  ? <IconLoader2 className="size-4 animate-spin" />
                                  : <IconBolt className="size-4" />}
                                {t("forms.customTool.draftPushButton", { defaultValue: "Push live draft" })}
                              </Button>
                              {draftStatus?.active && (
                                <Button
                                  type="button"
                                  variant="ghost"
                                  size="sm"
                                  onClick={() => clearDraftMutation.mutate()}
                                  disabled={clearDraftMutation.isPending}
                                  className="gap-2 sm:w-auto"
                                >
                                  {clearDraftMutation.isPending
                                    ? <IconLoader2 className="size-4 animate-spin" />
                                    : <IconBoltOff className="size-4" />}
                                  {t("forms.customTool.draftClearButton", { defaultValue: "Clear draft" })}
                                </Button>
                              )}
                            </>
                          )}
                          {pushDraftMutation.data && !pushDraftMutation.data.success && (
                            <div className="flex flex-1 items-start gap-1.5 rounded-md border border-destructive/40 bg-destructive/5 px-2.5 py-1.5 text-xs text-destructive">
                              <IconX className="size-3.5 mt-0.5 shrink-0" />
                              <div className="min-w-0 flex-1">
                                <div className="font-semibold">
                                  {t("forms.customTool.draftPushError", { defaultValue: "Draft failed to compile" })}
                                  {pushDraftMutation.data.line != null && (
                                    <span className="ml-1 font-normal opacity-80">
                                      ({t("forms.customTool.validateLineCol", {
                                        line: pushDraftMutation.data.line,
                                        col: pushDraftMutation.data.column ?? 1,
                                      })})
                                    </span>
                                  )}
                                </div>
                                <pre className="mt-1 whitespace-pre-wrap wrap-break-word font-mono text-[11px] opacity-90">
                                  {pushDraftMutation.data.error}
                                </pre>
                              </div>
                            </div>
                          )}
                          {draftStatus?.active && !pushDraftMutation.data?.success && (
                            <span className="inline-flex items-center gap-1.5 rounded-md border border-amber-500/40 bg-amber-500/10 px-2.5 py-1.5 text-xs text-amber-700 dark:text-amber-300">
                              <IconBolt className="size-3.5" />
                              {t("forms.customTool.draftActiveBanner", {
                                defaultValue: "Live draft active — your chat sessions run this version.",
                              })}
                            </span>
                          )}
                          {validation?.valid && (
                            <span className="inline-flex items-center gap-1.5 rounded-md border border-emerald-500/40 bg-emerald-500/10 px-2.5 py-1.5 text-xs text-emerald-700 dark:text-emerald-300">
                              <IconCheck className="size-3.5" />
                              {t("forms.customTool.validateOk")}
                            </span>
                          )}
                          {validation && !validation.valid && (
                            <div className="flex flex-1 items-start gap-1.5 rounded-md border border-destructive/40 bg-destructive/5 px-2.5 py-1.5 text-xs text-destructive">
                              <IconX className="size-3.5 mt-0.5 shrink-0" />
                              <div className="min-w-0 flex-1">
                                <div className="font-semibold">
                                  {t("forms.customTool.validateErrorTitle")}
                                  {validation.line != null && (
                                    <span className="ml-1 font-normal opacity-80">
                                      ({t("forms.customTool.validateLineCol", {
                                        line: validation.line,
                                        col: validation.column ?? 1,
                                      })})
                                    </span>
                                  )}
                                </div>
                                <pre className="mt-1 whitespace-pre-wrap wrap-break-word font-mono text-[11px] opacity-90">
                                  {validation.error}
                                </pre>
                              </div>
                            </div>
                          )}
                        </div>
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

              {/* Danger zone — only when editing an existing tool. The
                  original delete trigger lives in the desktop-only
                  CardHeader (hidden md:grid), which means mobile users
                  couldn't reach it AND desktop users with long forms
                  had to scroll back to the top to find the small trash
                  icon. This labelled button is always reachable, and it
                  reuses the same `open` state as the CardHeader trigger
                  so both surfaces open the SAME DialogDelete instance —
                  no duplicate modal. */}
              {!isNew && (
                <SectionCard variant="rose">
                  <SectionCard.Header
                    icon={IconAlertTriangle}
                    title={t("forms.customTool.dangerZoneTitle")}
                    description={t("forms.customTool.dangerZoneDesc")}
                  />
                  <SectionCard.Content>
                    <Button
                      type="button"
                      variant="destructive"
                      onClick={() => setOpen(true)}
                      className="w-full sm:w-auto"
                    >
                      <IconTrash className="size-4" />
                      {t("forms.customTool.deleteAction")}
                    </Button>
                  </SectionCard.Content>
                </SectionCard>
              )}
            </form>
          </Form>
        </CardContent>
      </Card>
    </div>
  );
};
