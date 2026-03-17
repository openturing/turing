"use client"
import { ROUTES } from "@/app/routes.const"
import { AvatarField } from "@/components/avatar-field"
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
import { SystemPromptEditor } from "@/components/ui/system-prompt-editor"
import { Textarea } from "@/components/ui/textarea"
import type { TurAIAgent } from "@/models/agent/ai-agent.model.ts"
import { TurAIAgentService } from "@/services/agent/ai-agent.service"
import { IconFileText, IconInfoCircle, IconToggleLeft } from "@tabler/icons-react"
import { useCallback, useEffect, useMemo, useState } from "react"
import { useForm } from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { toast } from "sonner"
import { FormActions } from "../ui/form-actions"
import { FormItemTwoColumns } from "../ui/form-item-two-columns"
import { GradientSwitch } from "../ui/gradient-switch"
import { SectionCard } from "../ui/section-card"

const turAIAgentService = new TurAIAgentService();
const urlBase = ROUTES.AI_AGENT_INSTANCE

interface Props {
  value: TurAIAgent;
  isNew: boolean;
}

export const AIAgentSettingsForm: React.FC<Props> = ({ value, isNew }) => {
  const form = useForm<TurAIAgent>({
    defaultValues: value
  });
  const navigate = useNavigate()
  const [avatarUrl, setAvatarUrl] = useState<string | undefined>(value.avatarUrl ?? undefined)

  useEffect(() => {
    form.reset({
      ...value,
      description: value.description ?? "",
      avatarUrl: value.avatarUrl ?? null,
      systemPrompt: value.systemPrompt ?? "",
      nativeTools: value.nativeTools ?? null,
      llmInstances: value.llmInstances ?? [],
      mcpServers: value.mcpServers ?? [],
    });
    setAvatarUrl(value.avatarUrl ?? undefined)
  }, [value])

  const title = form.watch("title") ?? ""
  const initials = useMemo(() => {
    if (!title.trim()) return "AG"
    const parts = title.trim().split(" ").filter(Boolean)
    if (parts.length === 1) return parts[0].charAt(0).toUpperCase()
    return parts[0].charAt(0).toUpperCase() + (parts.at(-1)?.charAt(0).toUpperCase() ?? "")
  }, [title])
  const avatarSeed = useMemo(() => value.id || title.trim() || "ai-agent", [value.id, title])

  const handleAvatarSelect = useCallback((url: string) => {
    setAvatarUrl(url)
    form.setValue("avatarUrl", url, { shouldDirty: true })
    toast.success("Avatar selected.")
  }, [form])

  const handleAvatarRemove = useCallback(() => {
    setAvatarUrl(undefined)
    form.setValue("avatarUrl", null, { shouldDirty: true })
    toast.success("Avatar removed.")
  }, [form])

  async function onSubmit(agent: TurAIAgent) {
    const payload: TurAIAgent = { ...agent, avatarUrl: avatarUrl ?? null }
    try {
      if (isNew) {
        const result = await turAIAgentService.create(payload);
        if (result) {
          toast.success(`The ${payload.title} AI Agent was saved`);
          navigate(`${urlBase}/${result.id}/settings`);
        } else {
          toast.error(`The ${payload.title} AI Agent was not saved`);
        }
      } else {
        const result = await turAIAgentService.update(payload);
        if (result) {
          toast.success(`The ${payload.title} AI Agent was updated`);
        } else {
          toast.error(`The ${payload.title} AI Agent was not updated`);
        }
      }
    } catch (error) {
      console.error("Form submission error", error);
      toast.error("Failed to submit the form. Please try again.");
    }
  }

  return (
    <div className="px-6">
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8">
          <SectionCard variant="blue">
            <SectionCard.Header icon={IconInfoCircle} title="General Information" description="Identity and purpose of this AI agent." />
            <SectionCard.Content>
              <AvatarField
                avatarUrl={avatarUrl}
                initials={initials}
                seed={avatarSeed}
                onSelect={handleAvatarSelect}
                onRemove={handleAvatarRemove}
                className="mb-4"
              >
                <div className="text-sm font-medium">Agent Avatar</div>
                <div className="text-xs text-muted-foreground">Used as the assistant avatar in this agent's chat tab.</div>
              </AvatarField>
              <FormField
                control={form.control}
                name="title"
                rules={{ required: "A title is required for this AI agent." }}
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>Title</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        A unique, descriptive name for this AI agent.
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <Input {...field} placeholder="e.g., Customer Support Agent" type="text" />
                      </FormControl>
                      <FormMessage />
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
              <FormField
                control={form.control}
                name="description"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Description</FormLabel>
                    <FormDescription>
                      Summarize the purpose or intended use of this AI agent.
                    </FormDescription>
                    <FormControl>
                      <Textarea placeholder="Describe what this agent does..." className="resize-none" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          <SectionCard variant="violet">
            <SectionCard.Header icon={IconFileText} title="System Prompt" description="The instruction that shapes the AI agent's personality and behavior." />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="systemPrompt"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Instruction</FormLabel>
                    <FormDescription>
                      Define the role, constraints, and style of responses for this agent.
                    </FormDescription>
                    <FormControl>
                      <SystemPromptEditor
                        value={field.value ?? ""}
                        onChange={field.onChange}
                        fieldRef={field.ref}
                        placeholder="You are a helpful assistant that..."
                        hint="This prompt is sent as a system message to the language model before every conversation. It defines the agent's behavior and personality."
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          <SectionCard variant="slate">
            <SectionCard.Header icon={IconToggleLeft} title="Status" description="Control whether this AI agent is active." />
            <SectionCard.Content>
              <FormField
                control={form.control}
                name="enabled"
                render={({ field }) => (
                  <FormItemTwoColumns>
                    <FormItemTwoColumns.Left>
                      <FormItemTwoColumns.Label>Enabled</FormItemTwoColumns.Label>
                      <FormItemTwoColumns.Description>
                        When disabled, the agent cannot be used in conversations.
                      </FormItemTwoColumns.Description>
                    </FormItemTwoColumns.Left>
                    <FormItemTwoColumns.Right>
                      <FormControl>
                        <GradientSwitch
                          checked={field.value === 1}
                          onCheckedChange={(checked) => {
                            field.onChange(checked ? 1 : 0);
                          }}
                        />
                      </FormControl>
                    </FormItemTwoColumns.Right>
                  </FormItemTwoColumns>
                )}
              />
            </SectionCard.Content>
          </SectionCard>

          <FormActions>
            <FormActions.Cancel onClick={() => navigate(urlBase)} />
            <FormActions.Submit />
          </FormActions>
        </form>
      </Form>
    </div>
  )
}
