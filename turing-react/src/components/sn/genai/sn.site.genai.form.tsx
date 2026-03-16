import {
    Dialog,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
} from "@/components/ui/dialog"
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
import { TurChatService } from "@/services/chat/chat.service"
import { TurLLMInstanceService } from "@/services/llm/llm.service"
import { TurSNSiteService } from "@/services/sn/sn.service"
import { TurStoreInstanceService } from "@/services/store/store.service"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"
import { IconBrain, IconFileDescription, IconLoader2, IconMessageChatbot, IconPower, IconSparkles } from "@tabler/icons-react"
import React, { useCallback, useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { toast } from "sonner"
import { Button } from "../../ui/button"
import { FormActions } from "../../ui/form-actions"
import { GradientButton } from "../../ui/gradient-button"
import { SectionCard } from "../../ui/section-card"
import { Textarea } from "../../ui/textarea"

const turSNSiteService = new TurSNSiteService();
const turLLMInstanceService = new TurLLMInstanceService();
const turStoreInstanceService = new TurStoreInstanceService();
const turGlobalSettingsService = new TurGlobalSettingsService();
const turChatService = new TurChatService();

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
        defaultValues: {
            ...snSite.turSNSiteGenAi
        },
    });
    const [llmInstances, setLlmInstances] = useState<TurLLMInstance[]>([]);
    const [storeInstances, setStoreInstances] = useState<TurStoreInstance[]>([]);
    const [defaultLlmId, setDefaultLlmId] = useState<string>("");
    const [isGenerating, setIsGenerating] = useState(false);
    const [showOverwriteDialog, setShowOverwriteDialog] = useState(false);

    useEffect(() => {
        turLLMInstanceService.query().then(setLlmInstances);
        turStoreInstanceService.query().then(setStoreInstances);
        turGlobalSettingsService.query().then((settings) => {
            setDefaultLlmId(settings.defaultLlmId ?? "");
        });
    }, []);

    useEffect(() => {
        form.reset({
            ...snSite.turSNSiteGenAi,
            sitePrompt: snSite.turSNSiteGenAi?.sitePrompt ?? "",
        });
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

    const generateSitePrompt = useCallback(async () => {
        if (!defaultLlmId || !snSite.description) return;

        setIsGenerating(true);
        let generated = "";

        await turChatService.sendStream(
            defaultLlmId,
            [{
                role: "user",
                content:
                    "Based on the following site description, generate a detailed prompt that describes what this site is about, " +
                    "its purpose, target audience, content types, and any relevant context. " +
                    "The prompt should be written as instructions for an AI assistant that will use it to generate reports, insights, and emails about this site. " +
                    "Write only the prompt text, no explanations or formatting.\n\n" +
                    `Site name: ${snSite.name}\n` +
                    `Site description: ${snSite.description}`,
            }],
            (token) => {
                generated += token;
                form.setValue("sitePrompt", generated);
            },
            () => {
                setIsGenerating(false);
                toast.success("Site prompt generated successfully.");
            },
            (error) => {
                setIsGenerating(false);
                console.error("Failed to generate site prompt", error);
                toast.error("Failed to generate site prompt. Check the default LLM configuration.");
            },
        );
    }, [defaultLlmId, snSite.name, snSite.description, form]);

    function handleGenerateClick() {
        const currentPrompt = form.getValues("sitePrompt") ?? "";
        if (currentPrompt.trim()) {
            setShowOverwriteDialog(true);
        } else {
            generateSitePrompt();
        }
    }

    function handleOverwriteConfirm() {
        setShowOverwriteDialog(false);
        generateSitePrompt();
    }

    async function onSubmit(data: TurSNSiteGenAi) {
        if (data.enabled && !hasAllVariables) {
            toast.error("The System Prompt must contain the tags {{question}} and {{information}}.");
            return;
        }

        try {
            const updatedSite = {
                ...snSite,
                turSNSiteGenAi: {
                    ...data,
                    sitePrompt: data.sitePrompt ?? "",
                },
            };
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

    const canGenerate = !!defaultLlmId && !!snSite.description && !isGenerating;

    return (
        <div className="px-6">
            <Form {...form}>
                <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-8">
                    <div className="space-y-4">
                        {/* Section 1: Site Prompt */}
                        <SectionCard variant="amber">
                            <SectionCard.Header icon={IconFileDescription} title="Site Prompt" description="Describe what this site is about. Used by the default LLM (Global Settings) for reports, insights, and emails." />
                            <SectionCard.Content>
                                <FormField
                                    control={form.control}
                                    name="sitePrompt"
                                    render={({ field }) => (
                                        <FormItem>
                                            <FormLabel>Site Description Prompt</FormLabel>
                                            <FormDescription>
                                                Write a detailed prompt describing what this site contains, its purpose, target audience, and any specific instructions for the AI. This prompt is used by the default LLM configured in Global Settings to generate reports, insights, and emails about this site.
                                            </FormDescription>
                                            <FormControl>
                                                <Textarea
                                                    placeholder="Example: This site indexes the internal knowledge base of our engineering team. It contains documentation about microservices architecture, deployment procedures, API specifications, and troubleshooting guides. The target audience is software engineers and DevOps team members..."
                                                    className="min-h-40"
                                                    disabled={isGenerating}
                                                    {...field}
                                                    value={field.value ?? ""}
                                                />
                                            </FormControl>
                                            <FormMessage />
                                        </FormItem>
                                    )}
                                />
                                <div className="flex items-center gap-3 pt-2">
                                    <GradientButton
                                        type="button"
                                        variant="outline"
                                        disabled={!canGenerate}
                                        onClick={handleGenerateClick}
                                    >
                                        {isGenerating ? (
                                            <IconLoader2 className="size-4 animate-spin" />
                                        ) : (
                                            <IconSparkles className="size-4" />
                                        )}
                                        {isGenerating ? "Generating..." : "Generate with AI"}
                                    </GradientButton>
                                    <span className="text-xs text-muted-foreground">
                                        {!defaultLlmId
                                            ? "Configure a default LLM in Global Settings to enable."
                                            : !snSite.description
                                                ? "Add a site description in Settings to enable."
                                                : "Generate a prompt based on the site description using the default LLM."}
                                    </span>
                                </div>
                            </SectionCard.Content>
                        </SectionCard>

                        {/* Section 2: RAG Activation */}
                        <SectionCard variant="blue">
                            <SectionCard.Header icon={IconPower} title="RAG" description="Retrieval-Augmented Generation for this site." />
                            <SectionCard.Content>
                                <FormField
                                    control={form.control}
                                    name="enabled"
                                    render={({ field }) => (
                                        <FormItemTwoColumns>
                                            <FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Label>Enable RAG</FormItemTwoColumns.Label>
                                                <FormItemTwoColumns.Description>
                                                    Enable Retrieval-Augmented Generation (RAG) to combine search results with a language model, providing AI-powered answers grounded in this site's indexed content.
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

                        {/* Section 3: Models */}
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

                        {/* Section 4: System Prompt */}
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

            <Dialog open={showOverwriteDialog} onOpenChange={setShowOverwriteDialog}>
                <DialogContent className="sm:max-w-md">
                    <DialogHeader>
                        <DialogTitle>Overwrite Site Prompt?</DialogTitle>
                        <DialogDescription>
                            The site description prompt already has content. Generating a new one will replace the existing text. This action cannot be undone.
                        </DialogDescription>
                    </DialogHeader>
                    <DialogFooter className="flex-col gap-2 sm:flex-row">
                        <Button variant="outline" onClick={() => setShowOverwriteDialog(false)}>
                            Cancel
                        </Button>
                        <GradientButton onClick={handleOverwriteConfirm}>
                            <IconSparkles className="size-4" />
                            Overwrite and Generate
                        </GradientButton>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
};
