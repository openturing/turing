"use client"
import {
  useCreatePersona,
  useDeletePersona,
  useUpdatePersona,
} from "@/api/queries/persona.queries"
import { ROUTES } from "@/app/routes.const"
import {
  Card,
  CardAction,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Form,
  FormControl,
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
  SelectValue,
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import type { TurMcpServer } from "@/models/mcp/mcp-server.model.ts"
import type { TurPersona } from "@/models/persona/persona.model.ts"
import type { TurStoreInstance } from "@/models/store/store-instance.model.ts"
import { TurMcpServerService } from "@/services/mcp/mcp-server.service"
import { TurStoreInstanceService } from "@/services/store/store.service"
import {
  IconAlertTriangle,
  IconBan,
  IconBookmark,
  IconMessage2,
  IconPalette,
  IconSettings,
  IconSparkles,
  IconTrash,
} from "@tabler/icons-react"
import { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { DialogDelete } from "../dialog.delete"
import { Button } from "../ui/button"
import { GradientSwitch } from "../ui/gradient-switch"
import { PromptEditor } from "../ui/prompt-editor"
import { SectionCard } from "../ui/section-card"
import { StickySaveBar } from "../ui/sticky-save-bar"

/**
 * System message used by the persona system-instruction "Help me write" sheet.
 * Persona system instructions describe the brand voice / role contract that
 * is prepended to the active chat — the helper LLM must shape its output
 * accordingly (not chat copy, not an end-user message).
 */
function buildPersonaSystemInstructionFieldInstruction(spec: {
  name?: string;
  description?: string;
}): string {
  return [
    "You are helping a user write the SYSTEM INSTRUCTION of a persona inside the Turing Enterprise Search platform.",
    "",
    "FIELD SEMANTICS:",
    "- A persona is a reusable voice profile attached to an AI agent.",
    "- The text becomes a system message prepended to chats that adopt this persona.",
    "- It must define identity, role, tone, audience, behavior boundaries, and any non-negotiable rules.",
    "- It is NOT shown to the end user — it talks TO the LLM about how to sound and behave.",
    "",
    "PERSONA CONTEXT (current form state):",
    `- Persona name: ${spec.name?.trim() || "(unset)"}`,
    `- Persona description: ${spec.description?.trim() || "(unset)"}`,
    "",
    "WRITING RULES:",
    "- Write in second person ('You are...', 'You should...', 'You must never...').",
    "- Anchor it in identity first (who this persona is), then behavior (how it speaks), then guardrails (what it refuses).",
    "- Prefer imperative bullet lists or short paragraphs over flowery prose.",
    "- Match the language the user wrote the brief in (Portuguese brief → Portuguese; English brief → English).",
    "",
    "OUTPUT RULES:",
    "- Return ONLY the resulting system instruction text.",
    "- Do NOT wrap in quotes, code fences, or markdown.",
    "- Do NOT include a preamble, headers, commentary, or examples of conversations.",
    "- If a current system instruction exists, prefer the smallest change that satisfies the user's instructions.",
  ].join("\n");
}

const NONE_VALUE = "__none__"
const TONES = ["FORMAL", "CASUAL", "TECHNICAL", "EXECUTIVE"] as const
const LANGUAGE_STYLES = [
  "NEUTRAL",
  "DIRECT",
  "NARRATIVE",
  "PERSUASIVE",
  "INSTRUCTIONAL",
] as const

const turStoreInstanceService = new TurStoreInstanceService()
const turMcpServerService = new TurMcpServerService()

const urlBase = ROUTES.PERSONA_INSTANCE

interface Props {
  value: TurPersona
  isNew: boolean
}

/**
 * Form fields are joined back into the persona payload on submit:
 * - termsToLines / linesToTerms convert between newline-friendly UI text
 *   and the pipe-separated DB format.
 * - fewShotStore / brandContextMcpServer are stored as full entity refs in
 *   form state, but the API only needs the {id} — handled in onSubmit.
 */
type FormShape = Omit<TurPersona, "mandatoryTerms" | "forbiddenTerms"> & {
  mandatoryTermsText: string
  forbiddenTermsText: string
}

const termsToLines = (raw?: string | null) =>
  (raw ?? "")
    .split("|")
    .map((t) => t.trim())
    .filter((t) => t.length > 0)
    .join("\n")

const linesToTerms = (text: string) =>
  text
    .split(/\r?\n/)
    .map((t) => t.trim())
    .filter((t) => t.length > 0)
    .join("|") || null

export const PersonaForm: React.FC<Props> = ({ value, isNew }) => {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)
  const [stores, setStores] = useState<TurStoreInstance[]>([])
  const [mcpServers, setMcpServers] = useState<TurMcpServer[]>([])

  const form = useForm<FormShape>({
    defaultValues: {
      ...value,
      mandatoryTermsText: termsToLines(value.mandatoryTerms),
      forbiddenTermsText: termsToLines(value.forbiddenTerms),
    },
  })

  const createMutation = useCreatePersona()
  const updateMutation = useUpdatePersona()
  const deleteMutation = useDeletePersona()

  useEffect(() => {
    turStoreInstanceService.query().then(setStores).catch(() => setStores([]))
    turMcpServerService.query().then(setMcpServers).catch(() => setMcpServers([]))
  }, [])

  useEffect(() => {
    form.reset({
      ...value,
      mandatoryTermsText: termsToLines(value.mandatoryTerms),
      forbiddenTermsText: termsToLines(value.forbiddenTerms),
    })
  }, [value, form])

  async function onSubmit(data: FormShape) {
    const { mandatoryTermsText, forbiddenTermsText, ...rest } = data
    const payload: TurPersona = {
      ...rest,
      mandatoryTerms: linesToTerms(mandatoryTermsText),
      forbiddenTerms: linesToTerms(forbiddenTermsText),
      verbosity: Number(rest.verbosity ?? 3),
      enabled: Number(rest.enabled ?? 1),
    }

    try {
      if (isNew) {
        const result = await createMutation.mutateAsync(payload)
        if (result) {
          toast.success(
            t("forms.common.saved", { name: payload.name, feature: t("persona.title") })
          )
          navigate(urlBase)
        } else {
          toast.error(
            t("forms.common.notSaved", { name: payload.name, feature: t("persona.title") })
          )
        }
      } else {
        const result = await updateMutation.mutateAsync(payload)
        if (result) {
          toast.success(
            t("forms.common.updated", { name: payload.name, feature: t("persona.title") })
          )
        } else {
          toast.error(
            t("forms.common.notUpdated", { name: payload.name, feature: t("persona.title") })
          )
        }
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error(t("forms.common.formSubmitFailed"))
    }
  }

  async function onDelete() {
    try {
      if (await deleteMutation.mutateAsync(value)) {
        toast.success(
          t("forms.common.deleted", { name: value.name, feature: t("persona.title") })
        )
        navigate(urlBase)
      } else {
        toast.error(
          t("forms.common.notDeleted", { name: value.name, feature: t("persona.title") })
        )
      }
    } catch (error) {
      console.error("Form submission error", error)
      toast.error(
        t("forms.common.notDeleted", { name: value.name, feature: t("persona.title") })
      )
    }
    setOpen(false)
  }

  const watchedName = form.watch("name")

  return (
    <div className="w-full px-0 md:px-4 py-4">
      <Form {...form}>
        <form
          onSubmit={form.handleSubmit(onSubmit)}
          className="space-y-4 md:space-y-6"
          autoComplete="off"
        >
          <StickySaveBar
            title={watchedName || (isNew ? t("persona.newInstance") : t("persona.title"))}
            onCancel={() => navigate(urlBase)}
          />

          <Card className="mx-auto border-0 shadow-none md:border md:shadow-sm rounded-none md:rounded-xl">
            <CardHeader className="hidden md:grid">
              <CardTitle className="text-2xl">
                {isNew ? t("persona.newInstance") : t("persona.title")}
              </CardTitle>
              <CardAction>
                {!isNew && (
                  <DialogDelete
                    feature={t("persona.title")}
                    name={value.name}
                    onDelete={onDelete}
                    open={open}
                    setOpen={setOpen}
                  />
                )}
              </CardAction>
              <CardDescription>{t("forms.persona.settings")}</CardDescription>
            </CardHeader>
            <CardContent>
              <div className="w-full max-w-2xl mx-auto py-2 md:py-8 flex flex-col gap-4">
                {/* General */}
                <SectionCard variant="blue">
                  <SectionCard.Header
                    icon={IconSettings}
                    title={t("forms.common.generalInfo")}
                    description={t("forms.persona.generalDesc")}
                  />
                  <SectionCard.Content>
                    <FormField
                      control={form.control}
                      name="name"
                      rules={{ required: t("forms.persona.nameRequired") }}
                      render={({ field }) => (
                        <FormItem className="w-full">
                          <FormLabel>{t("forms.common.name")}</FormLabel>
                          <FormControl>
                            <Input
                              {...field}
                              placeholder={t("forms.common.name")}
                              type="text"
                              className="w-full"
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="description"
                      render={({ field }) => (
                        <FormItem className="w-full">
                          <FormLabel>{t("forms.common.description")}</FormLabel>
                          <FormControl>
                            <Textarea
                              {...field}
                              value={field.value ?? ""}
                              rows={2}
                              maxLength={500}
                              placeholder={t("forms.persona.descriptionPlaceholder")}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="enabled"
                      render={({ field }) => (
                        <FormItem className="w-full flex flex-row justify-between items-center">
                          <FormLabel className="m-0">{t("forms.common.enabled")}</FormLabel>
                          <FormControl>
                            <GradientSwitch
                              checked={Number(field.value) === 1}
                              onCheckedChange={(checked) => field.onChange(checked ? 1 : 0)}
                            />
                          </FormControl>
                        </FormItem>
                      )}
                    />
                  </SectionCard.Content>
                </SectionCard>

                {/* System Instruction */}
                <SectionCard variant="violet">
                  <SectionCard.Header
                    icon={IconSparkles}
                    title={t("forms.persona.systemInstruction")}
                    description={t("forms.persona.systemInstructionDesc")}
                  />
                  <SectionCard.Content>
                    <FormField
                      control={form.control}
                      name="systemInstruction"
                      render={({ field }) => (
                        <FormItem className="w-full">
                          <FormControl>
                            <PromptEditor
                              value={field.value ?? ""}
                              onChange={field.onChange}
                              fieldRef={field.ref}
                              rows={8}
                              placeholder={t("forms.persona.systemInstructionPlaceholder")}
                              metaPrompt={{
                                fieldInstruction: buildPersonaSystemInstructionFieldInstruction({
                                  name: form.watch("name"),
                                  description: form.watch("description") ?? undefined,
                                }),
                                tone: "violet",
                                triggerLabel: t("forms.persona.helpWriteSystemInstruction"),
                                title: t("forms.persona.helpWriteSystemInstructionTitle"),
                                description: t("forms.persona.helpWriteSystemInstructionDescription"),
                                placeholder: t("forms.persona.helpWriteSystemInstructionPlaceholder"),
                                hint: t("forms.persona.helpWriteSystemInstructionHint"),
                                generateLabel: t("forms.persona.helpWriteSystemInstructionGenerate"),
                              }}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </SectionCard.Content>
                </SectionCard>

                {/* Style */}
                <SectionCard variant="emerald">
                  <SectionCard.Header
                    icon={IconPalette}
                    title={t("forms.persona.styleGuidelines")}
                    description={t("forms.persona.styleGuidelinesDesc")}
                  />
                  <SectionCard.Content>
                    <FormField
                      control={form.control}
                      name="tone"
                      render={({ field }) => (
                        <FormItem className="w-full">
                          <FormLabel>{t("forms.persona.tone")}</FormLabel>
                          <FormControl>
                            <Select
                              value={field.value ?? NONE_VALUE}
                              onValueChange={(v) =>
                                field.onChange(v === NONE_VALUE ? null : v)
                              }
                            >
                              <SelectTrigger className="w-full">
                                <SelectValue placeholder={t("forms.persona.tonePlaceholder")} />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value={NONE_VALUE}>
                                  {t("forms.persona.tonePlaceholder")}
                                </SelectItem>
                                {TONES.map((tone) => (
                                  <SelectItem key={tone} value={tone}>
                                    {t(`persona.tones.${tone}`)}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </FormControl>
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="verbosity"
                      rules={{
                        min: { value: 1, message: t("forms.persona.verbosityRange") },
                        max: { value: 5, message: t("forms.persona.verbosityRange") },
                      }}
                      render={({ field }) => (
                        <FormItem className="w-full">
                          <FormLabel>{t("forms.persona.verbosity")}</FormLabel>
                          <FormControl>
                            <Input
                              {...field}
                              type="number"
                              min={1}
                              max={5}
                              value={field.value ?? 3}
                              onChange={(e) => field.onChange(Number(e.target.value))}
                            />
                          </FormControl>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="languageStyle"
                      render={({ field }) => (
                        <FormItem className="w-full">
                          <FormLabel>{t("forms.persona.languageStyle")}</FormLabel>
                          <FormControl>
                            <Select
                              value={field.value ?? NONE_VALUE}
                              onValueChange={(v) =>
                                field.onChange(v === NONE_VALUE ? null : v)
                              }
                            >
                              <SelectTrigger className="w-full">
                                <SelectValue
                                  placeholder={t("forms.persona.languageStylePlaceholder")}
                                />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value={NONE_VALUE}>
                                  {t("forms.persona.languageStylePlaceholder")}
                                </SelectItem>
                                {LANGUAGE_STYLES.map((style) => (
                                  <SelectItem key={style} value={style}>
                                    {t(`persona.languageStyles.${style}`)}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </FormControl>
                        </FormItem>
                      )}
                    />
                  </SectionCard.Content>
                </SectionCard>

                {/* Vocabulary */}
                <SectionCard variant="amber">
                  <SectionCard.Header
                    icon={IconBan}
                    title={t("forms.persona.vocabulary")}
                    description={t("forms.persona.vocabularyDesc")}
                  />
                  <SectionCard.Content>
                    <FormField
                      control={form.control}
                      name="mandatoryTermsText"
                      render={({ field }) => (
                        <FormItem className="w-full">
                          <FormLabel>{t("forms.persona.mandatoryTerms")}</FormLabel>
                          <div className="text-muted-foreground text-sm font-normal mt-1">
                            {t("forms.persona.termsDesc")}
                          </div>
                          <FormControl>
                            <Textarea
                              {...field}
                              rows={3}
                              placeholder={t("forms.persona.mandatoryTermsPlaceholder")}
                            />
                          </FormControl>
                        </FormItem>
                      )}
                    />
                    <FormField
                      control={form.control}
                      name="forbiddenTermsText"
                      render={({ field }) => (
                        <FormItem className="w-full">
                          <FormLabel>{t("forms.persona.forbiddenTerms")}</FormLabel>
                          <div className="text-muted-foreground text-sm font-normal mt-1">
                            {t("forms.persona.termsDesc")}
                          </div>
                          <FormControl>
                            <Textarea
                              {...field}
                              rows={3}
                              placeholder={t("forms.persona.forbiddenTermsPlaceholder")}
                            />
                          </FormControl>
                        </FormItem>
                      )}
                    />
                  </SectionCard.Content>
                </SectionCard>

                {/* Few-Shot Repository */}
                <SectionCard variant="cyan">
                  <SectionCard.Header
                    icon={IconBookmark}
                    title={t("forms.persona.fewShot")}
                    description={t("forms.persona.fewShotDesc")}
                  />
                  <SectionCard.Content>
                    <FormField
                      control={form.control}
                      name="fewShotStore"
                      render={({ field }) => (
                        <FormItem className="w-full">
                          <FormLabel>{t("forms.persona.fewShotStore")}</FormLabel>
                          <FormControl>
                            <Select
                              value={field.value?.id ?? NONE_VALUE}
                              onValueChange={(v) => {
                                if (v === NONE_VALUE) {
                                  field.onChange(null)
                                } else {
                                  const store = stores.find((s) => s.id === v)
                                  field.onChange(store ?? null)
                                }
                              }}
                            >
                              <SelectTrigger className="w-full">
                                <SelectValue
                                  placeholder={t("forms.persona.fewShotStorePlaceholder")}
                                />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value={NONE_VALUE}>
                                  {t("forms.persona.fewShotStorePlaceholder")}
                                </SelectItem>
                                {stores.map((store) => (
                                  <SelectItem key={store.id} value={store.id}>
                                    {store.title}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </FormControl>
                        </FormItem>
                      )}
                    />
                  </SectionCard.Content>
                </SectionCard>

                {/* Brand Context MCP */}
                <SectionCard variant="rose">
                  <SectionCard.Header
                    icon={IconMessage2}
                    title={t("forms.persona.brandContext")}
                    description={t("forms.persona.brandContextDesc")}
                  />
                  <SectionCard.Content>
                    <FormField
                      control={form.control}
                      name="brandContextMcpServer"
                      render={({ field }) => (
                        <FormItem className="w-full">
                          <FormLabel>{t("forms.persona.brandContextMcp")}</FormLabel>
                          <FormControl>
                            <Select
                              value={field.value?.id ?? NONE_VALUE}
                              onValueChange={(v) => {
                                if (v === NONE_VALUE) {
                                  field.onChange(null)
                                } else {
                                  const mcp = mcpServers.find((m) => m.id === v)
                                  field.onChange(mcp ?? null)
                                }
                              }}
                            >
                              <SelectTrigger className="w-full">
                                <SelectValue
                                  placeholder={t("forms.persona.brandContextMcpPlaceholder")}
                                />
                              </SelectTrigger>
                              <SelectContent>
                                <SelectItem value={NONE_VALUE}>
                                  {t("forms.persona.brandContextMcpPlaceholder")}
                                </SelectItem>
                                {mcpServers.map((mcp) => (
                                  <SelectItem key={mcp.id} value={mcp.id}>
                                    {mcp.title}
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          </FormControl>
                        </FormItem>
                      )}
                    />
                  </SectionCard.Content>
                </SectionCard>

                {/* Danger zone — duplicates the desktop-only CardHeader
                    delete trigger as a labelled button that's visible on
                    every screen size AND doesn't require scrolling back
                    to the top of a long form to find. Reuses the same
                    `open`/`setOpen` state as the CardHeader trigger so
                    only ONE DialogDelete instance renders. */}
                {!isNew && (
                  <SectionCard variant="rose">
                    <SectionCard.Header
                      icon={IconAlertTriangle}
                      title={t("forms.persona.dangerZoneTitle")}
                      description={t("forms.persona.dangerZoneDesc")}
                    />
                    <SectionCard.Content>
                      <Button
                        type="button"
                        variant="destructive"
                        onClick={() => setOpen(true)}
                        className="w-full sm:w-auto"
                      >
                        <IconTrash className="size-4" />
                        {t("forms.persona.deleteAction")}
                      </Button>
                    </SectionCard.Content>
                  </SectionCard>
                )}
              </div>
            </CardContent>
          </Card>
        </form>
      </Form>
    </div>
  )
}
