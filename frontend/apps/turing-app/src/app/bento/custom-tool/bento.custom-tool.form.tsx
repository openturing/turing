"use client"
import {
  useClearCustomToolDraft,
  useCreateCustomTool,
  useCustomToolDescriptor,
  useCustomToolDraftStatus,
  usePushCustomToolDraft,
  useUpdateCustomTool,
} from "@/api/queries/custom-tool.queries"
import { ROUTES } from "@/app/routes.const"
import { groovyAutocomplete } from "@/components/custom-tool/groovy-autocomplete"
import { VibeCodingButton } from "@/components/custom-tool/vibe-coding-button"
import { BentoFormSection, BentoSaveBar, type BentoIdentity, type BentoShellFormState } from "@/components/bento"
import { Button } from "@/components/ui/button"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { Input } from "@/components/ui/input"
import { PromptEditor } from "@/components/ui/prompt-editor"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { CustomToolParameter, CustomToolPrimitive, TurCustomTool } from "@/models/customtool/custom-tool.model.ts"
import { TurCustomToolService, type ValidateScriptResponse } from "@/services/customtool/custom-tool.service"
import { StreamLanguage } from "@codemirror/language"
import { groovy } from "@codemirror/legacy-modes/mode/groovy"
import { oneDark } from "@codemirror/theme-one-dark"
import { IconBolt, IconBoltOff, IconBraces, IconCheck, IconCode, IconList, IconLoader2, IconPlayerPlay, IconX } from "@tabler/icons-react"
import CodeMirror from "@uiw/react-codemirror"
import { toast } from "@viglet/viglet-design-system"
import { useEffect, useMemo, useState } from "react"
import { useFieldArray, useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

const turCustomToolService = new TurCustomToolService()
const urlBase = ROUTES.BENTO_CUSTOM_TOOL_INSTANCE

const PARAM_PRIMITIVES: CustomToolPrimitive[] = ["string", "integer", "number", "boolean"]
const RETURN_PRIMITIVES: CustomToolPrimitive[] = ["string", "integer", "number", "boolean", "html", "markdown", "json"]

interface FormShape extends Omit<TurCustomTool, "parametersJson"> {
  parameters: CustomToolParameter[]
}

interface Props {
  value: TurCustomTool
  isNew: boolean
  staged?: BentoIdentity
  onStateChange?: (state: BentoShellFormState) => void
}

/** DOM id used by hero-anchored submit buttons via the `form="..."` attribute. */
export const BENTO_CUSTOM_TOOL_FORM_ID = "bento-custom-tool-form"

const DEFAULT_SCRIPT = `// Groovy. Args available as variables (e.g. \`name\`) and as the \`args\` map.
// Return any value — it will be coerced to string for the LLM.
return "Hello " + (args.name ?: "world")
`

function buildGroovyFieldInstruction(spec: {
  title?: string
  llmDescription?: string
  parameters?: CustomToolParameter[]
  returnType?: CustomToolPrimitive
}): string {
  const params = spec.parameters?.length
    ? spec.parameters.filter((p) => p.name?.trim()).map((p) => `${p.name}: ${p.type}`).join(", ")
    : "(none)"
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
  ].join("\n")
}

function parseParameters(json?: string | null): CustomToolParameter[] {
  if (!json?.trim()) return []
  try {
    const parsed = JSON.parse(json)
    if (Array.isArray(parsed)) {
      return parsed.filter((p) => p && typeof p.name === "string" && typeof p.type === "string")
    }
  } catch {
    /* ignore */
  }
  return []
}

export const BentoCustomToolForm: React.FC<Props> = ({ value, isNew, staged, onStateChange }) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [validating, setValidating] = useState(false)
  const [validation, setValidation] = useState<ValidateScriptResponse | null>(null)

  const createMutation = useCreateCustomTool()
  const updateMutation = useUpdateCustomTool()
  const { data: editorDescriptor } = useCustomToolDescriptor()
  const groovyExtensions = useMemo(() => {
    const base = [StreamLanguage.define(groovy)]
    return editorDescriptor ? [...base, groovyAutocomplete(editorDescriptor)] : base
  }, [editorDescriptor])

  const draftToolId = isNew ? undefined : value.id
  const { data: draftStatus } = useCustomToolDraftStatus(draftToolId)
  const pushDraftMutation = usePushCustomToolDraft(draftToolId)
  const clearDraftMutation = useClearCustomToolDraft(draftToolId)

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
  })
  const { fields, append, remove } = useFieldArray({ control: form.control, name: "parameters" })

  const isDirty = form.formState.isDirty
  const isSubmitting = form.formState.isSubmitting
  useEffect(() => {
    onStateChange?.({ isDirty, isSubmitting })
  }, [isDirty, isSubmitting, onStateChange])

  useEffect(() => {
    form.reset({
      ...value,
      description: value.description ?? "",
      llmDescription: value.llmDescription ?? "",
      groovyScript: value.groovyScript ?? DEFAULT_SCRIPT,
      returnType: value.returnType ?? "string",
      parameters: parseParameters(value.parametersJson),
      enabled: value.enabled ?? 1,
    }, { keepDirtyValues: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  async function onValidate() {
    if (validating) return
    const script = form.getValues("groovyScript") ?? ""
    setValidating(true)
    setValidation(null)
    try {
      setValidation(await turCustomToolService.validate(script))
    } catch (error) {
      console.error("Validate error", error)
      setValidation({ valid: false, error: t("forms.customTool.validateUnreachable") })
    } finally {
      setValidating(false)
    }
  }

  async function onSubmit(formValue: FormShape) {
    if (!staged?.title?.trim()) {
      toast.error(t("forms.customTool.titleRequired"))
      return
    }
    const { parameters, ...rest } = formValue
    const payload: TurCustomTool = {
      ...rest,
      title: staged.title,
      description: staged.description,
      icon: staged.icon,
      enabled: staged.enabled,
      parametersJson: JSON.stringify(parameters.filter((p) => p.name?.trim())),
    }
    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.title, feature: t("customTool.title") }))
          navigate(urlBase)
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: t("customTool.title") }))
        }
      } else {
        const result = await updateMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.title, feature: t("customTool.title") }))
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.title, feature: t("customTool.title") }))
        }
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error(t("forms.common.formSubmitFailed"))
    }
  }

  const stagedTitle = staged?.title?.trim() ?? ""
  const titleMissing = !stagedTitle
  // Save bar is present on any editable detail page so it stays suspended on
  // scroll; Save is disabled until there is something to persist.
  const showSaveBar = true

  return (
    <Form {...form}>
      <form id={BENTO_CUSTOM_TOOL_FORM_ID} onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5" autoComplete="off">
        {showSaveBar && (
          <>
            <div aria-hidden className="bento-save-bar-spacer" />
            <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
              <BentoSaveBar
                title={stagedTitle || (isNew ? t("customTool.newCustomTool") : t("customTool.title"))}
                disabled={titleMissing || (!isNew && !form.formState.isDirty)}
                titleMissing={titleMissing}
                dirty={isNew || form.formState.isDirty}
                loading={isSubmitting}
                onCancel={() => navigate(urlBase)}
              />
            </div>
          </>
        )}

        {/* LLM Description */}
        <BentoFormSection
          icon={IconBraces}
          tone="violet"
          title={t("forms.customTool.llmDescriptionTitle")}
          description={t("forms.customTool.llmDescriptionDesc")}
        >
          <FormField
            control={form.control}
            name="llmDescription"
            rules={{ required: t("forms.customTool.llmDescriptionRequired") }}
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.customTool.llmDescriptionLabel")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.customTool.llmDescriptionHint")}</p>
                <FormControl>
                  <PromptEditor
                    value={field.value ?? ""}
                    onChange={field.onChange}
                    fieldRef={field.ref}
                    placeholder={t("forms.customTool.llmDescriptionPlaceholder")}
                    rows={4}
                    metaPrompt={{
                      brief: form.watch("llmDescriptionMetaPrompt") ?? "",
                      onBriefChange: (v) => form.setValue("llmDescriptionMetaPrompt", v, { shouldDirty: true }),
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
        </BentoFormSection>

        {/* Parameters & Return Type */}
        <BentoFormSection
          icon={IconList}
          tone="blue"
          title={t("forms.customTool.parametersTitle")}
          description={t("forms.customTool.parametersDesc")}
        >
          <div className="w-full space-y-2">
            {fields.length === 0 && (
              <p className="text-xs italic text-muted-foreground">{t("forms.customTool.parametersEmpty")}</p>
            )}
            {fields.map((field, index) => (
              <div key={field.id} className="grid grid-cols-1 gap-2 rounded-md border p-3 sm:grid-cols-[minmax(0,1fr)_10rem_auto] sm:items-end">
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
                            <SelectItem key={p} value={p}>{t(`forms.customTool.primitive.${p}`)}</SelectItem>
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
            <Button type="button" variant="outline" size="sm" onClick={() => append({ name: "", type: "string" })}>
              {t("forms.customTool.addParameter")}
            </Button>
          </div>

          <div className="mt-4 flex w-full flex-row items-center justify-between gap-4 border-t pt-4">
            <div className="flex flex-col">
              <FormLabel>{t("forms.customTool.returnType")}</FormLabel>
              <p className="text-sm text-muted-foreground">{t("forms.customTool.returnTypeDesc")}</p>
            </div>
            <div className="max-w-xs flex-1">
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
                          <SelectItem key={p} value={p}>{t(`forms.customTool.primitive.${p}`)}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </FormItem>
                )}
              />
            </div>
          </div>
        </BentoFormSection>

        {/* Groovy Script */}
        <BentoFormSection
          icon={IconCode}
          tone="emerald"
          title={t("forms.customTool.scriptTitle")}
          description={t("forms.customTool.scriptDesc")}
        >
          <FormField
            control={form.control}
            name="groovyScript"
            rules={{ required: t("forms.customTool.scriptRequired") }}
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.customTool.scriptLabel")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.customTool.scriptHint")}</p>
                <FormControl>
                  <div className="overflow-hidden rounded-md border">
                    <CodeMirror
                      value={field.value ?? ""}
                      height="360px"
                      theme={oneDark}
                      extensions={groovyExtensions}
                      onChange={(val) => {
                        field.onChange(val)
                        if (validation) setValidation(null)
                      }}
                      basicSetup={{ lineNumbers: true, foldGutter: true, highlightActiveLine: true }}
                    />
                  </div>
                </FormControl>
                <div className="mt-2 flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center">
                  <Button type="button" variant="outline" size="sm" onClick={onValidate} disabled={validating || !field.value?.trim()} className="gap-2 sm:w-auto">
                    {validating ? <IconLoader2 className="size-4 animate-spin" /> : <IconPlayerPlay className="size-4" />}
                    {validating ? t("forms.customTool.validateRunning") : t("forms.customTool.validateButton")}
                  </Button>
                  <VibeCodingButton
                    value={field.value ?? ""}
                    onChange={(val) => {
                      field.onChange(val)
                      if (validation) setValidation(null)
                    }}
                    metaPrompt={form.watch("groovyMetaPrompt") ?? ""}
                    onMetaPromptChange={(v) => form.setValue("groovyMetaPrompt", v, { shouldDirty: true })}
                    fieldInstruction={buildGroovyFieldInstruction({
                      title: staged?.title,
                      llmDescription: form.watch("llmDescription"),
                      parameters: form.watch("parameters"),
                      returnType: form.watch("returnType"),
                    })}
                  />
                  {draftToolId && (
                    <>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => pushDraftMutation.mutate(field.value ?? "")}
                        disabled={pushDraftMutation.isPending || !field.value?.trim()}
                        className="gap-2 sm:w-auto"
                        title={t("forms.customTool.draftPushHint", { defaultValue: "Push this script as your live draft — your chat sessions will run it instead of the saved version." })}
                      >
                        {pushDraftMutation.isPending ? <IconLoader2 className="size-4 animate-spin" /> : <IconBolt className="size-4" />}
                        {t("forms.customTool.draftPushButton", { defaultValue: "Push live draft" })}
                      </Button>
                      {draftStatus?.active && (
                        <Button type="button" variant="ghost" size="sm" onClick={() => clearDraftMutation.mutate()} disabled={clearDraftMutation.isPending} className="gap-2 sm:w-auto">
                          {clearDraftMutation.isPending ? <IconLoader2 className="size-4 animate-spin" /> : <IconBoltOff className="size-4" />}
                          {t("forms.customTool.draftClearButton", { defaultValue: "Clear draft" })}
                        </Button>
                      )}
                    </>
                  )}
                  {pushDraftMutation.data && !pushDraftMutation.data.success && (
                    <div className="flex flex-1 items-start gap-1.5 rounded-md border border-destructive/40 bg-destructive/5 px-2.5 py-1.5 text-xs text-destructive">
                      <IconX className="mt-0.5 size-3.5 shrink-0" />
                      <div className="min-w-0 flex-1">
                        <div className="font-semibold">
                          {t("forms.customTool.draftPushError", { defaultValue: "Draft failed to compile" })}
                          {pushDraftMutation.data.line != null && (
                            <span className="ml-1 font-normal opacity-80">
                              ({t("forms.customTool.validateLineCol", { line: pushDraftMutation.data.line, col: pushDraftMutation.data.column ?? 1 })})
                            </span>
                          )}
                        </div>
                        <pre className="mt-1 whitespace-pre-wrap wrap-break-word font-mono text-[11px] opacity-90">{pushDraftMutation.data.error}</pre>
                      </div>
                    </div>
                  )}
                  {draftStatus?.active && !pushDraftMutation.data?.success && (
                    <span className="inline-flex items-center gap-1.5 rounded-md border border-amber-500/40 bg-amber-500/10 px-2.5 py-1.5 text-xs text-amber-700 dark:text-amber-300">
                      <IconBolt className="size-3.5" />
                      {t("forms.customTool.draftActiveBanner", { defaultValue: "Live draft active — your chat sessions run this version." })}
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
                      <IconX className="mt-0.5 size-3.5 shrink-0" />
                      <div className="min-w-0 flex-1">
                        <div className="font-semibold">
                          {t("forms.customTool.validateErrorTitle")}
                          {validation.line != null && (
                            <span className="ml-1 font-normal opacity-80">
                              ({t("forms.customTool.validateLineCol", { line: validation.line, col: validation.column ?? 1 })})
                            </span>
                          )}
                        </div>
                        <pre className="mt-1 whitespace-pre-wrap wrap-break-word font-mono text-[11px] opacity-90">{validation.error}</pre>
                      </div>
                    </div>
                  )}
                </div>
                <FormMessage />
              </FormItem>
            )}
          />
        </BentoFormSection>
      </form>
    </Form>
  )
}
