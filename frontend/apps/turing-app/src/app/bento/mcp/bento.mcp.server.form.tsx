"use client"
import { useCreateMcpServer, useUpdateMcpServer } from "@/api/queries/mcp-server.queries"
import { ROUTES } from "@/app/routes.const"
import { BentoFormSection, BentoSaveBar, type BentoIdentity, type BentoShellFormState } from "@/components/bento"
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from "@/components/ui/form"
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { Input } from "@/components/ui/input"
import { PromptEditor } from "@/components/ui/prompt-editor"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select"
import type { TurMcpServer } from "@/models/mcp/mcp-server.model.ts"
import { IconBraces, IconPlug, IconServer2, IconTerminal2 } from "@tabler/icons-react"
import { toast } from "@viglet/viglet-design-system"
import { useEffect } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"

const urlBase = ROUTES.BENTO_MCP_INSTANCE

interface Props {
  value: TurMcpServer
  isNew: boolean
  readOnly?: boolean
  staged?: BentoIdentity
  onStateChange?: (state: BentoShellFormState) => void
}

/** DOM id used by hero-anchored submit buttons via the `form="..."` attribute. */
export const BENTO_MCP_FORM_ID = "bento-mcp-server-form"

export const BentoMcpServerForm: React.FC<Props> = ({ value, isNew, readOnly = false, staged, onStateChange }) => {
  const { t } = useTranslation()
  const form = useForm<TurMcpServer>({ defaultValues: value })
  const selectedConnectionType = form.watch("connectionType")
  const navigate = useNavigate()

  const createMutation = useCreateMcpServer()
  const updateMutation = useUpdateMcpServer()

  const isDirty = form.formState.isDirty
  const isSubmitting = form.formState.isSubmitting
  useEffect(() => {
    onStateChange?.({ isDirty, isSubmitting })
  }, [isDirty, isSubmitting, onStateChange])

  useEffect(() => {
    form.reset({
      ...value,
      description: value.description ?? "",
      llmInstructions: value.llmInstructions ?? "",
      url: value.url ?? "",
      command: value.command ?? "",
      args: value.args ?? "",
      type: value.type ?? "SYNC",
      connectionType: value.connectionType ?? "HTTP",
      transportType: value.transportType ?? "SSE",
    }, { keepDirtyValues: true })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  async function onSubmit(mcpServer: TurMcpServer) {
    if (!staged?.title?.trim()) {
      toast.error(t("forms.mcp.titleRequired"))
      return
    }
    const payload: TurMcpServer = {
      ...mcpServer,
      ...staged,
      url: mcpServer.url?.trim() || undefined,
      command: mcpServer.command?.trim() || undefined,
      args: mcpServer.args?.trim() || undefined,
    }
    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.saved", { name: payload.title, feature: t("mcp.title") }))
          navigate(urlBase)
        } else {
          toast.error(t("forms.common.notSaved", { name: payload.title, feature: t("mcp.title") }))
        }
      } else {
        const result = await updateMutation.mutateAsync(payload)
        if (result) {
          toast.success(t("forms.common.updated", { name: payload.title, feature: t("mcp.title") }))
        } else {
          toast.error(t("forms.common.notUpdated", { name: payload.title, feature: t("mcp.title") }))
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
  const showSaveBar = !readOnly

  return (
    <Form {...form}>
      <form id={BENTO_MCP_FORM_ID} onSubmit={form.handleSubmit(onSubmit)} className="flex flex-col gap-5" autoComplete="off">
        {showSaveBar && (
          <>
            <div aria-hidden className="bento-save-bar-spacer" />
            <div className="bento-fade-in fixed inset-x-0 top-20 z-20 mx-auto w-full max-w-7xl px-4 md:px-8">
              <BentoSaveBar
                title={stagedTitle || (isNew ? t("mcp.newMcpServer") : t("mcp.title"))}
                disabled={titleMissing || (!isNew && !form.formState.isDirty)}
                titleMissing={titleMissing}
                dirty={isNew || form.formState.isDirty}
                loading={isSubmitting}
                badges={<BentoSaveBar.Badge>{selectedConnectionType}</BentoSaveBar.Badge>}
                onCancel={() => navigate(urlBase)}
              />
            </div>
          </>
        )}

        {/* LLM Instructions */}
        <BentoFormSection
          icon={IconBraces}
          tone="violet"
          title={t("forms.mcp.llmInstructionsTitle")}
          description={t("forms.mcp.llmInstructionsDesc")}
        >
          <FormField
            control={form.control}
            name="llmInstructions"
            render={({ field }) => (
              <FormItem className="w-full">
                <FormLabel>{t("forms.mcp.llmInstructionsLabel")}</FormLabel>
                <p className="text-sm text-muted-foreground">{t("forms.mcp.llmInstructionsHint")}</p>
                <FormControl>
                  <PromptEditor
                    value={field.value ?? ""}
                    onChange={field.onChange}
                    fieldRef={field.ref}
                    placeholder={t("forms.mcp.llmInstructionsPlaceholder")}
                    rows={4}
                    metaPrompt={{
                      brief: form.watch("llmInstructionsMetaPrompt") ?? "",
                      onBriefChange: (v) => form.setValue("llmInstructionsMetaPrompt", v, { shouldDirty: true }),
                      fieldInstruction:
                        "This text is injected into an AI agent's system prompt as guidance for an attached MCP (Model Context Protocol) server — it tells the model how and when to use the tools that server exposes. " +
                        "MUST be written in English regardless of any other instruction. " +
                        "MUST clearly state what the server is for, when the agent should reach for its tools, and any constraints or preferred order of use. " +
                        "Be concise but specific; avoid filler. Do not include preamble, quotes, markdown headings, or commentary — return only the instruction text.",
                      tone: "violet",
                      triggerLabel: t("forms.mcp.helpWriteLlmInstructions"),
                      title: t("forms.mcp.helpWriteLlmInstructionsTitle"),
                      description: t("forms.mcp.helpWriteLlmInstructionsDescription"),
                      placeholder: t("forms.mcp.helpWriteLlmInstructionsPlaceholder"),
                      hint: t("forms.mcp.helpWriteLlmInstructionsHint"),
                      generateLabel: t("forms.mcp.helpWriteLlmInstructionsGenerate"),
                    }}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </BentoFormSection>

        {/* Connection */}
        <BentoFormSection
          icon={IconPlug}
          tone="blue"
          title={t("forms.mcp.connection")}
          description={t("forms.mcp.connectionDesc")}
        >
          <FormField
            control={form.control}
            name="connectionType"
            rules={{ required: t("forms.mcp.connectionTypeRequired") }}
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.mcp.connectionType")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>{t("forms.mcp.connectionTypeDesc")}</FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <Select onValueChange={field.onChange} value={field.value} disabled={readOnly}>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder={t("forms.mcp.selectConnectionType")} />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="HTTP">{t("forms.mcp.http")}</SelectItem>
                        <SelectItem value="COMMAND">{t("forms.mcp.command")}</SelectItem>
                      </SelectContent>
                    </Select>
                  </FormControl>
                </FormItemTwoColumns.Right>
                <FormMessage />
              </FormItemTwoColumns>
            )}
          />
          {selectedConnectionType === "HTTP" && (
            <FormField
              control={form.control}
              name="transportType"
              render={({ field }) => (
                <FormItemTwoColumns>
                  <FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Label>{t("forms.mcp.transportType")}</FormItemTwoColumns.Label>
                    <FormItemTwoColumns.Description>{t("forms.mcp.transportTypeDesc")}</FormItemTwoColumns.Description>
                  </FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Right>
                    <FormControl>
                      <Select onValueChange={field.onChange} value={field.value ?? "SSE"} disabled={readOnly}>
                        <SelectTrigger className="w-full">
                          <SelectValue placeholder={t("forms.mcp.selectTransportType")} />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="SSE">{t("forms.mcp.transportSse")}</SelectItem>
                          <SelectItem value="STREAMABLE_HTTP">{t("forms.mcp.transportStreamableHttp")}</SelectItem>
                        </SelectContent>
                      </Select>
                    </FormControl>
                  </FormItemTwoColumns.Right>
                  <FormMessage />
                </FormItemTwoColumns>
              )}
            />
          )}
          <FormField
            control={form.control}
            name="type"
            rules={{ required: t("forms.mcp.typeRequired") }}
            render={({ field }) => (
              <FormItemTwoColumns>
                <FormItemTwoColumns.Left>
                  <FormItemTwoColumns.Label>{t("forms.mcp.type")}</FormItemTwoColumns.Label>
                  <FormItemTwoColumns.Description>{t("forms.mcp.typeDesc")}</FormItemTwoColumns.Description>
                </FormItemTwoColumns.Left>
                <FormItemTwoColumns.Right>
                  <FormControl>
                    <Select onValueChange={field.onChange} value={field.value} disabled={readOnly}>
                      <SelectTrigger className="w-full">
                        <SelectValue placeholder={t("forms.mcp.selectType")} />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="SYNC">{t("forms.mcp.sync")}</SelectItem>
                        <SelectItem value="ASYNC">{t("forms.mcp.async")}</SelectItem>
                      </SelectContent>
                    </Select>
                  </FormControl>
                </FormItemTwoColumns.Right>
                <FormMessage />
              </FormItemTwoColumns>
            )}
          />
        </BentoFormSection>

        {/* Endpoint */}
        <BentoFormSection
          icon={selectedConnectionType === "COMMAND" ? IconTerminal2 : IconServer2}
          tone="emerald"
          title={t("forms.mcp.endpointSection")}
          description={selectedConnectionType === "COMMAND" ? t("forms.mcp.endpointDescCommand") : t("forms.mcp.endpointDescHttp")}
        >
          {selectedConnectionType === "HTTP" && (
            <FormField
              control={form.control}
              name="url"
              render={({ field }) => (
                <FormItem className="w-full">
                  <FormLabel>{t("forms.mcp.url")}</FormLabel>
                  <p className="text-sm text-muted-foreground">{t("forms.mcp.urlDesc")}</p>
                  <FormControl>
                    <Input placeholder={t("forms.mcp.urlPlaceholder")} type="text" className="w-full" readOnly={readOnly} {...field} value={field.value ?? ""} />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          )}
          {selectedConnectionType === "COMMAND" && (
            <>
              <FormField
                control={form.control}
                name="command"
                render={({ field }) => (
                  <FormItem className="w-full">
                    <FormLabel>{t("forms.mcp.commandLabel")}</FormLabel>
                    <p className="text-sm text-muted-foreground">{t("forms.mcp.commandDesc")}</p>
                    <FormControl>
                      <Input placeholder={t("forms.mcp.commandPlaceholder")} type="text" className="w-full" readOnly={readOnly} {...field} value={field.value ?? ""} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="args"
                render={({ field }) => (
                  <FormItem className="w-full">
                    <FormLabel>{t("forms.mcp.arguments")}</FormLabel>
                    <p className="text-sm text-muted-foreground">{t("forms.mcp.argumentsDesc")}</p>
                    <FormControl>
                      <Input placeholder={t("forms.mcp.argumentsPlaceholder")} type="text" className="w-full" readOnly={readOnly} {...field} value={field.value ?? ""} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </>
          )}
        </BentoFormSection>
      </form>
    </Form>
  )
}
