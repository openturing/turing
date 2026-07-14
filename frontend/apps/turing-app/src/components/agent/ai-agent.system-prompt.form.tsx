"use client"
import { useUpdateAiAgent } from "@/api/queries/ai-agent.queries"
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
import { PromptEditor } from "@/components/ui/prompt-editor"
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs"
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts"
import { IconDeviceFloppy, IconFileText, IconX } from "@tabler/icons-react"
import { toast } from "@viglet/viglet-design-system"
import { useEffect } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router-dom"
import { StickyPageHeader } from "../sticky-page-header"
import { GradientButton } from "../ui/gradient-button"
import { SectionCard } from "../ui/section-card"
import { BentoAgentSubHero } from "@/app/bento/ai-agent/bento.ai-agent.sub-hero"
import { SystemPromptWarningsPanel } from "./system-prompt-warnings-panel"

const urlBase = ROUTES.AI_AGENT_INSTANCE

interface Props {
  value: TurAIAgent;
  /**
   * Which shell the form lives in. In "bento" the console `StickyPageHeader`
   * is dropped (the bento page supplies its own `BentoHero`) and Save/Cancel
   * render as a footer action row instead.
   */
  chrome?: "console" | "bento";
  /** Route base for the Cancel navigation — defaults to the console list. */
  baseRoute?: string;
}

/**
 * System message used by the PromptEditor "Help me write" sheet. Tells the LLM
 * that the field is the agent's system prompt (the behavioral contract sent on
 * every conversation), so generated text is shaped accordingly. Moved here from
 * the Settings form when the system prompt got its own page.
 */
function buildSystemPromptFieldInstruction(spec: {
  title?: string;
  agentDescription?: string;
}): string {
  return [
    "You are helping a user write the SYSTEM PROMPT of an AI agent inside the Turing Enterprise Search platform.",
    "",
    "FIELD SEMANTICS:",
    "- This text becomes the system message prepended to every conversation the agent has.",
    "- It defines the agent's persona, scope, behavior boundaries, tone, and any operating instructions.",
    "- It is NOT shown to the end user — it talks TO the LLM about how to behave.",
    "",
    "AGENT CONTEXT (current form state):",
    `- Agent name: ${spec.title?.trim() || "(unset)"}`,
    `- Agent description: ${spec.agentDescription?.trim() || "(unset)"}`,
    "",
    "WRITING RULES:",
    "- Write in second person ('You are...', 'You should...').",
    "- Be specific about scope (what the agent does AND what it refuses).",
    "- Prefer imperative bullet lists or short paragraphs over flowery prose.",
    "- Match the language the user wrote the brief in (Portuguese brief → Portuguese system prompt; English brief → English).",
    "",
    "OUTPUT RULES:",
    "- Return ONLY the resulting system prompt text.",
    "- Do NOT wrap in quotes, code fences, or markdown.",
    "- Do NOT include a preamble, headers, commentary, or examples of conversations.",
    "- If a current system prompt exists, prefer the smallest change that satisfies the user's instructions.",
  ].join("\n");
}

export const AIAgentSystemPromptForm: React.FC<Props> = ({ value, chrome = "console", baseRoute = urlBase }) => {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const updateMutation = useUpdateAiAgent();
  const isBento = chrome === "bento";

  const actions = (
    <>
      <GradientButton type="submit" size="sm">
        <IconDeviceFloppy className="size-4" />
        {t("forms.formActions.saveChanges")}
      </GradientButton>
      <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(baseRoute)}>
        <IconX className="size-4" />
        {t("forms.formActions.cancel")}
      </GradientButton>
    </>
  );

  // Whole agent in form state (defaultValues) so a save round-trips every
  // field the update endpoint reads — we only render the systemPrompt field.
  const form = useForm<TurAIAgent>({ defaultValues: value });

  useEffect(() => {
    form.reset({
      ...value,
      systemPrompt: value.systemPrompt ?? "",
    });
  }, [value]);

  async function onSubmit(agent: TurAIAgent) {
    try {
      const result = await updateMutation.mutateAsync({ ...agent });
      if (result) {
        toast.success(t("forms.common.updated", { name: value.title, feature: t("aiAgent.title") }));
      } else {
        toast.error(t("forms.common.notUpdated", { name: value.title, feature: t("aiAgent.title") }));
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error(t("forms.common.formSubmitFailed"));
    }
  }

  return (
    <div className="space-y-4 px-4 lg:px-6 pb-8">
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
          {isBento ? (
            /* Standard agent form-hero (T736): Save/Cancel live in the hero and
               fade into a fixed sticky save-bar on scroll — matching every
               sibling agent sub-page (settings/intent/…). */
            <BentoAgentSubHero
              agentId={value.id}
              agentTitle={value.title || t("aiAgent.title")}
              icon={IconFileText}
              tone="violet"
              title={t("aiAgent.systemPrompt.title", { defaultValue: "System Prompt" })}
              subtitle={t("aiAgent.systemPrompt.description", {
                defaultValue: "The behavioral contract sent to the model on every turn.",
              })}
              onCancel={() => navigate(baseRoute)}
              loading={updateMutation.isPending}
              dirty={form.formState.isDirty}
            />
          ) : (
            <StickyPageHeader>
              <StickyPageHeader.Title
                icon={IconFileText}
                feature={t("aiAgent.systemPrompt.title", { defaultValue: "System Prompt" })}
                description={t("aiAgent.systemPrompt.description", {
                  defaultValue: "The behavioral contract sent to the model on every turn.",
                })}
              />
              <StickyPageHeader.Actions>{actions}</StickyPageHeader.Actions>
            </StickyPageHeader>
          )}

          <Tabs defaultValue="editor" className="space-y-4">
            <TabsList>
              <TabsTrigger value="editor">
                {t("aiAgent.systemPrompt.tabs.editor", { defaultValue: "Editor" })}
              </TabsTrigger>
              <TabsTrigger value="conflict">
                {t("aiAgent.systemPrompt.tabs.conflictCheck", { defaultValue: "Conflict check" })}
              </TabsTrigger>
            </TabsList>

            <TabsContent value="editor" className="space-y-4">
              <SectionCard variant="violet">
                <SectionCard.Header
                  icon={IconFileText}
                  title={t("forms.agentSettings.systemPrompt")}
                  description={t("forms.agentSettings.systemPromptDesc")}
                />
                <SectionCard.Content>
                  <FormField
                    control={form.control}
                    name="systemPrompt"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>{t("forms.agentSettings.instruction")}</FormLabel>
                        <FormDescription>{t("forms.agentSettings.instructionDesc")}</FormDescription>
                        <FormControl>
                          <PromptEditor
                            value={field.value ?? ""}
                            onChange={field.onChange}
                            fieldRef={field.ref}
                            placeholder={t("forms.agentSettings.instructionPlaceholder")}
                            hint={t("forms.agentSettings.instructionHint")}
                            metaPrompt={{
                              brief: form.watch("systemPromptMetaPrompt") ?? "",
                              onBriefChange: (v) =>
                                form.setValue("systemPromptMetaPrompt", v, { shouldDirty: true }),
                              fieldInstruction: buildSystemPromptFieldInstruction({
                                title: form.watch("title"),
                                agentDescription: form.watch("description"),
                              }),
                              tone: "violet",
                              triggerLabel: t("forms.agentSettings.helpWriteSystemPrompt"),
                              title: t("forms.agentSettings.helpWriteSystemPromptTitle"),
                              description: t("forms.agentSettings.helpWriteSystemPromptDescription"),
                              placeholder: t("forms.agentSettings.helpWriteSystemPromptPlaceholder"),
                              hint: t("forms.agentSettings.helpWriteSystemPromptHint"),
                              generateLabel: t("forms.agentSettings.helpWriteSystemPromptGenerate"),
                            }}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                </SectionCard.Content>
              </SectionCard>
            </TabsContent>

            {/* Conflict validation reflects the SAVED configuration — it
                refetches automatically after a save (the update mutation
                invalidates the ai-agents query tree). Its buttons are
                type="button", so living inside the <form> never triggers an
                accidental submit. The segment-by-segment Live Preview moved to
                its own page under the agent's "Chat" sidebar group. */}
            <TabsContent value="conflict" className="space-y-4">
              <SystemPromptWarningsPanel agentId={value.id} />
            </TabsContent>
          </Tabs>
        </form>
      </Form>
    </div>
  );
};
