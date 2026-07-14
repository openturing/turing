"use client"
import type { PersonaFormContext } from "@/components/persona/persona.instance.form.layout"
import {
  FormControl,
  FormField,
  FormItem,
  FormMessage,
} from "@/components/ui/form"
import { PromptEditor } from "@/components/ui/prompt-editor"
import { SectionCard, useSectionChrome } from "@/components/ui/section-card"
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb"
import { IconSparkles } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"
import { useOutletContext } from "react-router-dom"

/**
 * System message used by the persona system-instruction "Help me write" sheet.
 * Persona system instructions describe the brand voice / role contract that
 * is prepended to the active chat — the helper LLM must shape its output
 * accordingly (not chat copy, not an end-user message).
 */
function buildPersonaSystemInstructionFieldInstruction(spec: {
  name?: string
  description?: string
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
  ].join("\n")
}

/**
 * "Instrução do Sistema" section of the persona editor — the voice/role
 * contract prepended to chats that adopt this persona, with an AI-assisted
 * "Help me write" sheet.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.4
 */
export default function PersonaInstanceSystemInstructionSection() {
  const { t } = useTranslation()
  const { form } = useOutletContext<PersonaFormContext>()
  const chrome = useSectionChrome()
  useSubPageBreadcrumb(t("persona.sidebar.systemInstruction"))

  return (
    <div className={`w-full px-2 md:px-6 py-2 md:py-8 flex flex-col gap-4${chrome === "bento" ? "" : " max-w-2xl mx-auto"}`}>
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
    </div>
  )
}
