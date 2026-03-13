import {
    Form,
    FormControl,
    FormDescription,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from "@/components/ui/form"
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns"
import { GradientSwitch } from "@/components/ui/gradient-switch"
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select"
import { allVariablesPresent, SystemPromptEditor, type PromptVariable } from "@/components/ui/system-prompt-editor"
import type { TurLLMInstance } from "@/models/llm/llm-instance.model"
import type { TurSNSiteGenAi } from "@/models/sn/sn-site-genai.model"
import type { TurSNSite } from "@/models/sn/sn-site.model"
import type { TurStoreInstance } from "@/models/store/store-instance.model"
import { TurLLMInstanceService } from "@/services/llm/llm.service"
import { TurSNSiteService } from "@/services/sn/sn.service"
import { TurStoreInstanceService } from "@/services/store/store.service"
import { IconBrain, IconMessageChatbot, IconPower } from "@tabler/icons-react"
import { FormActions } from "../../ui/form-actions"
import { SectionCard } from "../../ui/section-card"
import React, { useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { toast } from "sonner"

const turSNSiteService = new TurSNSiteService();
const turLLMInstanceService = new TurLLMInstanceService();
const turStoreInstanceService = new TurStoreInstanceService();

const DEFAULT_SYSTEM_PROMPT =
    "Using only this rag data. Answer in Portuguese. You are a helpful assistant that can answer questions about the web site:\n" +
    "\n" +
    "Question:\n" +
    "{{question}}\n" +
    "\n" +
    "Base your answer on the following information:\n" +
    "{{information}}";

interface Props {
    snSite: TurSNSite;
}

export const SNSiteGenAiForm: React.FC<Props> = ({ snSite }) => {
    const form = useForm<TurSNSiteGenAi>({
        defaultValues: snSite.turSNSiteGenAi,
    });
    const [llmInstances, setLlmInstances] = useState<TurLLMInstance[]>([]);
    const [storeInstances, setStoreInstances] = useState<TurStoreInstance[]>([]);

    useEffect(() => {
        turLLMInstanceService.query().then(setLlmInstances);
        turStoreInstanceService.query().then(setStoreInstances);
    }, []);

    useEffect(() => {
        form.reset(snSite.turSNSiteGenAi);
    }, [snSite]);

    const enabled = form.watch("enabled");
    const systemPrompt = form.watch("systemPrompt") || "";

    const PROMPT_VARIABLES: PromptVariable[] = [
        { token: "{{question}}", label: "question" },
        { token: "{{information}}", label: "information" },
    ];

    const hasAllVariables = allVariablesPresent(systemPrompt, PROMPT_VARIABLES);

    function handleEnabledChange(checked: boolean) {
        form.setValue("enabled", checked);
        if (checked && !form.getValues("systemPrompt")) {
            form.setValue("systemPrompt", DEFAULT_SYSTEM_PROMPT);
        }
    }

    async function onSubmit(data: TurSNSiteGenAi) {
        if (data.enabled && !hasAllVariables) {
            toast.error("The System Prompt must contain the tags {{question}} and {{information}}.");
            return;
        }

        try {
            const updatedSite = { ...snSite, turSNSiteGenAi: data };
            const result = await turSNSiteService.update(updatedSite);
            if (result) {
                toast.success(`${snSite.name} semantic navigation site was updated.`);
            } else {
                toast.error("Failed to update site. Please try again.");
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error("Failed to update site. Please try again.");
        }
    }

    return (
        <div className="px-6">
            <Form {...form}>
                <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8">
                    <div className="space-y-4">
                        {/* Section 1: Activation */}
                        <SectionCard variant="blue">
                            <SectionCard.Header icon={IconPower} title="Activation" description="Enable AI-powered answers for this site." />
                            <SectionCard.Content>
                                <FormField
                                    control={form.control}
                                    name="enabled"
                                    render={({ field }) => (
                                        <FormItemTwoColumns>
                                            <FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Label>Enable Generative AI</FormItemTwoColumns.Label>
                                                <FormItemTwoColumns.Description>
                                                    Turn on AI-powered answers and semantic search for this site.
                                                </FormItemTwoColumns.Description>
                                            </FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Right>
                                                <FormControl>
                                                    <GradientSwitch
                                                        checked={field.value}
                                                        onCheckedChange={handleEnabledChange}
                                                    />
                                                </FormControl>
                                            </FormItemTwoColumns.Right>
                                        </FormItemTwoColumns>
                                    )}
                                />
                            </SectionCard.Content>
                        </SectionCard>

                        {/* Section 2: Models */}
                        {enabled && (
                            <SectionCard variant="violet">
                                <SectionCard.Header icon={IconBrain} title="AI Model & Embedding Store" description="Language model and vector store configuration." />
                                <SectionCard.Content>
                                    {/* Language Model */}
                                    <FormField
                                        control={form.control}
                                        name="turLLMInstance"
                                        rules={{ required: enabled && "Language model is required." }}
                                        render={({ field }) => (
                                            <FormItemTwoColumns>
                                                <FormItemTwoColumns.Left>
                                                    <FormItemTwoColumns.Label>Language Model</FormItemTwoColumns.Label>
                                                    <FormItemTwoColumns.Description>
                                                        Choose the AI language model for generating answers.
                                                    </FormItemTwoColumns.Description>
                                                </FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Right>
                                                    <FormControl>
                                                        <Select
                                                            onValueChange={(val) => {
                                                                const instance = llmInstances.find((i) => i.id === val);
                                                                field.onChange(instance ?? null);
                                                            }}
                                                            value={field.value?.id || "__none__"}
                                                        >
                                                            <SelectTrigger className="w-full">
                                                                <SelectValue placeholder="-- No Language Model --" />
                                                            </SelectTrigger>
                                                            <SelectContent>
                                                                <SelectItem value="__none__">-- No Language Model --</SelectItem>
                                                                {llmInstances.map((instance) => (
                                                                    <SelectItem key={instance.id} value={instance.id}>
                                                                        {instance.title}
                                                                    </SelectItem>
                                                                ))}
                                                            </SelectContent>
                                                        </Select>
                                                    </FormControl>
                                                    <FormMessage />
                                                </FormItemTwoColumns.Right>
                                            </FormItemTwoColumns>
                                        )}
                                    />

                                    {/* Embedding Store */}
                                    <FormField
                                        control={form.control}
                                        name="turStoreInstance"
                                        rules={{ required: enabled && "Embedding store is required." }}
                                        render={({ field }) => (
                                            <FormItemTwoColumns>
                                                <FormItemTwoColumns.Left>
                                                    <FormItemTwoColumns.Label>Embedding Store</FormItemTwoColumns.Label>
                                                    <FormItemTwoColumns.Description>
                                                        Select the vector store for semantic search and RAG.
                                                    </FormItemTwoColumns.Description>
                                                </FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Right>
                                                    <FormControl>
                                                        <Select
                                                            onValueChange={(val) => {
                                                                const instance = storeInstances.find((i) => i.id === val);
                                                                field.onChange(instance ?? null);
                                                            }}
                                                            value={field.value?.id || "__none__"}
                                                        >
                                                            <SelectTrigger className="w-full">
                                                                <SelectValue placeholder="-- No Embedding Store --" />
                                                            </SelectTrigger>
                                                            <SelectContent>
                                                                <SelectItem value="__none__">-- No Embedding Store --</SelectItem>
                                                                {storeInstances.map((instance) => (
                                                                    <SelectItem key={instance.id} value={instance.id}>
                                                                        {instance.title}
                                                                    </SelectItem>
                                                                ))}
                                                            </SelectContent>
                                                        </Select>
                                                    </FormControl>
                                                    <FormMessage />
                                                </FormItemTwoColumns.Right>
                                            </FormItemTwoColumns>
                                        )}
                                    />
                                </SectionCard.Content>
                            </SectionCard>
                        )}

                        {/* Section 3: System Prompt */}
                        {enabled && (
                            <SectionCard variant="emerald">
                                <SectionCard.Header icon={IconMessageChatbot} title="System Prompt" description="Define the prompt template for the AI agent." />
                                <SectionCard.Content>
                                    <FormField
                                        control={form.control}
                                        name="systemPrompt"
                                        rules={{ required: enabled && "System prompt is required." }}
                                        render={({ field }) => (
                                            <FormItem>
                                                <FormLabel>Prompt Template</FormLabel>
                                                <FormDescription>
                                                    Define the prompt for the AI agent. Use <span className="font-mono font-bold text-amber-600">{"{{question}}"}</span> and <span className="font-mono font-bold text-amber-600">{"{{information}}"}</span> to insert user queries and retrieved content.
                                                </FormDescription>
                                                <FormControl>
                                                    <SystemPromptEditor
                                                        value={field.value ?? ""}
                                                        onChange={field.onChange}
                                                        fieldRef={field.ref}
                                                        placeholder="Ex: Use {{information}} to answer {{question}}..."
                                                        variables={PROMPT_VARIABLES}
                                                        requireVariables
                                                        hint="For RAG to work, the prompt must contain {{question}} and {{information}}."
                                                    />
                                                </FormControl>
                                                <FormMessage />
                                            </FormItem>
                                        )}
                                    />
                                </SectionCard.Content>
                            </SectionCard>
                        )}
                    </div>

                    <FormActions>
                        <FormActions.Cancel onClick={() => form.reset(snSite.turSNSiteGenAi)} />
                        <FormActions.Submit disabled={enabled && !hasAllVariables} />
                    </FormActions>
                </form>
            </Form>
        </div>
    );
};
