import {
    Form,
    FormControl,
    FormDescription,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import { FormActions } from "@/components/ui/form-actions";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import type { TurGlobalSettings } from "@/models/system/global-settings.model";
import { TurGlobalSettingsService } from "@/services/system/global-settings.service";
import { SectionCard } from "@/components/ui/section-card";
import {
    IconHash,
    IconTerminal2,
} from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

const turGlobalSettingsService = new TurGlobalSettingsService();

export default function GlobalSettingsPage() {
    const form = useForm<TurGlobalSettings>({
        defaultValues: { decimalSeparator: "DOT" },
    });
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        turGlobalSettingsService
            .query()
            .then((settings) => {
                form.reset(settings);
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
        <div className="px-4 lg:px-6 max-w-2xl space-y-6">
            <Form {...form}>
                <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6">
                    {/* Numeric Formatting Section */}
                    <SectionCard variant="amber">
                        <SectionCard.StaticHeader icon={IconHash} title="Numeric Formatting" description="Control how numbers and currencies are displayed across the platform." />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="decimalSeparator"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>Decimal Separator</FormLabel>
                                        <FormDescription>
                                            Defines how decimal values are typed in admin forms (including currency amount format).
                                        </FormDescription>
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
                                        <FormMessage />
                                    </FormItem>
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
                        </SectionCard.Content>
                    </SectionCard>

                    {/* Runtime Section */}
                    <SectionCard variant="emerald">
                        <SectionCard.StaticHeader icon={IconTerminal2} title="Runtime Environment" description="Configure external tool paths and execution environment." />
                        <SectionCard.Content>
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
                        </SectionCard.Content>
                    </SectionCard>

                    {/* Submit */}
                    <FormActions className="border-t-0 pt-2">
                        <FormActions.Submit loading={isSaving} disabled={isLoading}>
                            Save Settings
                        </FormActions.Submit>
                    </FormActions>
                </form>
            </Form>
        </div>
    );
}
