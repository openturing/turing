import {
    Form,
    FormControl,
    FormDescription,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from "@/components/ui/form";
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns";
import { Input } from "@/components/ui/input";
import { FormActions } from "@/components/ui/form-actions";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { SubPageHeader } from "@/components/sub.page.header";
import type { TurGlobalSettings } from "@/models/system/global-settings.model";
import { TurGlobalSettingsService } from "@/services/system/global-settings.service";
import { SectionCard } from "@/components/ui/section-card";
import { IconSettings } from "@tabler/icons-react";
import type { TurLLMInstance } from "@/models/llm/llm-instance.model";
import { TurLLMInstanceService } from "@/services/llm/llm.service";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

const turGlobalSettingsService = new TurGlobalSettingsService();
const turLLMInstanceService = new TurLLMInstanceService();

export default function GlobalSettingsPage() {
    const form = useForm<TurGlobalSettings>({
        defaultValues: { decimalSeparator: "DOT" },
    });
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [llmInstances, setLlmInstances] = useState<TurLLMInstance[]>([]);

    useEffect(() => {
        Promise.all([
            turGlobalSettingsService.query(),
            turLLMInstanceService.query(),
        ])
            .then(([settings, instances]) => {
                form.reset(settings);
                setLlmInstances(instances.filter((i) => i.enabled === 1));
            })
            .catch(() => {
                toast.error("Failed to load global settings. Using default values.");
            })
            .finally(() => setIsLoading(false));
    }, [form]);

    const onSubmit = async (settings: TurGlobalSettings) => {
        try {
            setIsSaving(true);
            const response = await turGlobalSettingsService.update(settings);
            form.reset(response);
            toast.success("Global settings were updated.");
        } catch (error) {
            console.error("Form submission error", error);
            toast.error("Failed to update global settings.");
        } finally {
            setIsSaving(false);
        }
    };

    const decimalSeparator = form.watch("decimalSeparator") ?? "DOT";
    const decimalExample = decimalSeparator === "COMMA" ? "1.500,75" : "1,500.75";
    const currencyExample =
        decimalSeparator === "COMMA" ? "150,75,BRL" : "150.75,BRL";

    return (
        <>
            <SubPageHeader
                icon={IconSettings}
                feature="Settings"
                name="Global Settings"
                description="Platform-wide configuration and preferences."
            />
            <Form {...form}>
                <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-6 px-6">
                    <SectionCard variant="amber">
                        <SectionCard.Header icon={IconSettings} title="Global Settings" description="Platform-wide configuration, formatting, and AI preferences." />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="decimalSeparator"
                                render={({ field }) => (
                                    <FormItemTwoColumns>
                                        <FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Label>Decimal Separator</FormItemTwoColumns.Label>
                                            <FormItemTwoColumns.Description>
                                                Defines how decimal values are typed in admin forms (including currency amount format).
                                            </FormItemTwoColumns.Description>
                                        </FormItemTwoColumns.Left>
                                        <FormItemTwoColumns.Right>
                                            <FormControl>
                                                <Select onValueChange={field.onChange} value={field.value} disabled={isLoading}>
                                                    <SelectTrigger className="w-full max-w-xs">
                                                        <SelectValue placeholder="Choose..." />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value="DOT">Dot (.)</SelectItem>
                                                        <SelectItem value="COMMA">Comma (,)</SelectItem>
                                                    </SelectContent>
                                                </Select>
                                            </FormControl>
                                        </FormItemTwoColumns.Right>
                                    </FormItemTwoColumns>
                                )}
                            />
                            <div className="flex gap-4">
                                <div className="flex-1 rounded-lg bg-muted/40 px-4 py-3">
                                    <span className="text-xs text-muted-foreground">Decimal preview</span>
                                    <p className="text-sm font-mono font-semibold mt-0.5">{decimalExample}</p>
                                </div>
                                <div className="flex-1 rounded-lg bg-muted/40 px-4 py-3">
                                    <span className="text-xs text-muted-foreground">Currency format</span>
                                    <p className="text-sm font-mono font-semibold mt-0.5">{currencyExample}</p>
                                </div>
                            </div>
                            <FormField
                                control={form.control}
                                name="pythonExecutable"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Python Executable Path</FormLabel>
                                        <FormDescription>
                                            Absolute path to the Python interpreter used by the code interpreter tool.
                                            Leave empty to auto-detect from known system locations.
                                        </FormDescription>
                                        <FormControl>
                                            <Input
                                                className="max-w-md font-mono text-sm"
                                                placeholder="/usr/bin/python3"
                                                disabled={isLoading}
                                                {...field}
                                                value={field.value ?? ""}
                                            />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                            <FormField
                                control={form.control}
                                name="defaultLlmId"
                                render={({ field }) => (
                                    <FormItemTwoColumns>
                                        <FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Label>Default LLM</FormItemTwoColumns.Label>
                                            <FormItemTwoColumns.Description>
                                                LLM instance used for AI suggestions across all screens
                                                (e.g., log analysis, monitoring summaries).
                                            </FormItemTwoColumns.Description>
                                        </FormItemTwoColumns.Left>
                                        <FormItemTwoColumns.Right>
                                            <FormControl>
                                                <Select
                                                    onValueChange={(v) => field.onChange(v === "none" ? "" : v)}
                                                    value={field.value || "none"}
                                                    disabled={isLoading}
                                                >
                                                    <SelectTrigger className="w-full max-w-xs">
                                                        <SelectValue placeholder="Select an LLM instance..." />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value="none">None</SelectItem>
                                                        {llmInstances.map((instance) => (
                                                            <SelectItem key={instance.id} value={instance.id}>
                                                                {instance.title}
                                                            </SelectItem>
                                                        ))}
                                                    </SelectContent>
                                                </Select>
                                            </FormControl>
                                        </FormItemTwoColumns.Right>
                                    </FormItemTwoColumns>
                                )}
                            />
                        </SectionCard.Content>
                    </SectionCard>

                    <FormActions>
                        <FormActions.Submit loading={isSaving} disabled={isLoading}>
                            Save Settings
                        </FormActions.Submit>
                    </FormActions>
                </form>
            </Form>
        </>
    );
}
