import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion"
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
import { GradientButton } from "@/components/ui/gradient-button"
import { GradientSwitch } from "@/components/ui/gradient-switch"
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select"
import { Textarea } from "@/components/ui/textarea"
import type { TurLLMInstance } from "@/models/llm/llm-instance.model"
import type { TurSNSiteGenAi } from "@/models/sn/sn-site-genai.model"
import type { TurSNSite } from "@/models/sn/sn-site.model"
import type { TurStoreInstance } from "@/models/store/store-instance.model"
import { TurLLMInstanceService } from "@/services/llm/llm.service"
import { TurSNSiteService } from "@/services/sn/sn.service"
import { TurStoreInstanceService } from "@/services/store/store.service"
import { Info, PlusCircle } from "lucide-react"
import React, { useEffect, useRef, useState } from "react"
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

    // Tag validation logic
    const hasQuestion = systemPrompt.includes("{{question}}");
    const hasInformation = systemPrompt.includes("{{information}}");

    function handleEnabledChange(checked: boolean) {
        form.setValue("enabled", checked);
        if (checked && !form.getValues("systemPrompt")) {
            form.setValue("systemPrompt", DEFAULT_SYSTEM_PROMPT);
        }
    }
    const textareaRef = useRef<HTMLTextAreaElement>(null);
    const insertVariable = (variable: string) => {
        const textarea = textareaRef.current;
        const currentPrompt = form.getValues("systemPrompt") || "";

        if (textarea) {
            const start = textarea.selectionStart;
            const end = textarea.selectionEnd;

            const before = currentPrompt.slice(0, start);
            const after = currentPrompt.slice(end);
            const newPrompt = before + variable + after;

            form.setValue("systemPrompt", newPrompt, { shouldValidate: true });

            setTimeout(() => {
                textarea.focus();
                textarea.setSelectionRange(start + variable.length, start + variable.length);
            }, 0);
        } else {
            form.setValue("systemPrompt", currentPrompt + variable, { shouldValidate: true });
        }
    };

    async function onSubmit(data: TurSNSiteGenAi) {
        if (data.enabled && (!hasQuestion || !hasInformation)) {
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
                <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-8 py-8 px-0">
                    <Accordion
                        type="multiple"
                        defaultValue={["section-activation", "section-models", "section-prompt"]}
                        className="w-full space-y-4"
                    >
                        {/* Section 1: Activation */}
                        <AccordionItem value="section-activation" className="border rounded-lg px-6">
                            <AccordionTrigger className="hover:no-underline">
                                <div className="flex items-center gap-2">
                                    <span className="text-lg font-semibold">Activation</span>
                                </div>
                            </AccordionTrigger>
                            <AccordionContent className="flex flex-col gap-6 pt-4">
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
                            </AccordionContent>
                        </AccordionItem>

                        {/* Section 2: Models */}
                        {enabled && (
                            <AccordionItem value="section-models" className="border rounded-lg px-6">
                                <AccordionTrigger className="hover:no-underline">
                                    <div className="flex items-center gap-2">
                                        <span className="text-lg font-semibold">AI Model & Embedding Store</span>
                                    </div>
                                </AccordionTrigger>
                                <AccordionContent className="flex flex-col gap-6 pt-4">
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
                                </AccordionContent>
                            </AccordionItem>
                        )}

                        {/* Section 3: System Prompt */}
                        {enabled && (
                            <AccordionItem value="section-prompt" className="border rounded-lg px-6">
                                <AccordionTrigger className="hover:no-underline">
                                    <div className="flex items-center gap-2">
                                        <span className="text-lg font-semibold">System Prompt</span>
                                    </div>
                                </AccordionTrigger>
                                <AccordionContent className="flex flex-col gap-6 pt-4">
                                    <FormField
                                        control={form.control}
                                        name="systemPrompt"
                                        rules={{ required: enabled && "System prompt is required." }}
                                        render={({ field }) => (
                                            <FormItem>
                                                <FormLabel>Prompt Template</FormLabel>
                                                <FormDescription>
                                                    Define the prompt for the AI agent. Use <span className="font-mono font-bold text-amber-600">{'{question}'}</span> and <span className="font-mono font-bold text-amber-600">{'{information}'}</span> to insert user queries and retrieved content. This guides the AI's response style and context.
                                                </FormDescription>
                                                <div className="flex gap-2 mt-2">
                                                    <button
                                                        type="button"
                                                        onClick={() => insertVariable("{{question}}")}
                                                        className="text-[10px] flex items-center gap-1 bg-amber-500/10 text-amber-600 border border-amber-500/20 px-2 py-1 rounded hover:bg-amber-500/20 transition-all font-mono"
                                                    >
                                                        <PlusCircle className="w-3 h-3" /> {"{{question}}"}
                                                    </button>
                                                    <button
                                                        type="button"
                                                        onClick={() => insertVariable("{{information}}")}
                                                        className="text-[10px] flex items-center gap-1 bg-amber-500/10 text-amber-600 border border-amber-500/20 px-2 py-1 rounded hover:bg-amber-500/20 transition-all font-mono"
                                                    >
                                                        <PlusCircle className="w-3 h-3" /> {"{{information}}"}
                                                    </button>
                                                </div>
                                                <FormControl>
                                                    <Textarea
                                                        {...field}
                                                        ref={(e) => {
                                                            field.ref(e);
                                                            (textareaRef as any).current = e;
                                                        }}
                                                        rows={8}
                                                        placeholder="Ex: Use {{information}} to answer {{question}}..."
                                                        className="font-mono text-sm leading-relaxed mt-2"
                                                    />
                                                </FormControl>
                                                <div className="flex gap-4 mt-2">
                                                    <StatusBadge label="question" active={hasQuestion} />
                                                    <StatusBadge label="information" active={hasInformation} />
                                                </div>
                                                <div className="flex items-start gap-2 mt-2 p-2 bg-slate-50 rounded-md dark:bg-slate-900 border">
                                                    <Info className="w-4 h-4 text-amber-500 shrink-0 mt-0.5" />
                                                    <p className="text-[11px] text-muted-foreground">
                                                        For RAG to work, the prompt must contain <span className="font-bold text-amber-600">{'{question}'}</span> and <span className="font-bold text-amber-600">{'{information}'}</span>.
                                                    </p>
                                                </div>
                                                <FormMessage />
                                            </FormItem>
                                        )}
                                    />
                                </AccordionContent>
                            </AccordionItem>
                        )}
                    </Accordion>

                    {/* Action Footer */}
                    <div className="flex justify-end gap-2 pt-4">
                        <GradientButton
                            type="button"
                            variant="outline"
                            onClick={() => form.reset(snSite.turSNSiteGenAi)}
                            className="w-full sm:w-auto"
                        >
                            Cancel
                        </GradientButton>
                        <GradientButton
                            type="submit"
                            disabled={enabled && (!hasQuestion || !hasInformation)}
                            className="w-full sm:w-auto"
                        >
                            Save Changes
                        </GradientButton>
                    </div>
                </form>
            </Form>
        </div>
    );
};

function StatusBadge({ label, active }: { readonly label: string; readonly active: boolean }) {
    return (
        <div className={`flex items-center gap-1.5 px-2 py-1 rounded-full border text-[10px] font-bold transition-all ${active
            ? "bg-emerald-500/10 text-emerald-600 border-emerald-500/20"
            : "bg-slate-100 text-slate-400 border-slate-200 opacity-60"
            }`}>
            <div className={`w-1.5 h-1.5 rounded-full ${active ? "bg-emerald-500 animate-pulse" : "bg-slate-300"}`} />
            {label.toUpperCase()}
        </div>
    );
}