import { ROUTES } from "@/app/routes.const";
import { SubPageHeader } from "@/components/sub.page.header";
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
import { GradientButton } from "@/components/ui/gradient-button";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { useBreadcrumb } from "@/contexts/breadcrumb.context";
import type { TurGlobalSettings } from "@/models/system/global-settings.model";
import { TurGlobalSettingsService } from "@/services/system/global-settings.service";
import { IconSettings } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

const turGlobalSettingsService = new TurGlobalSettingsService();

export default function GlobalSettingsPage() {
    const form = useForm<TurGlobalSettings>({
        defaultValues: { decimalSeparator: "DOT" },
    });
    const [isLoading, setIsLoading] = useState(true);
    const { pushItem, popItem } = useBreadcrumb();

    useEffect(() => {
        pushItem({ label: "Global Settings", href: ROUTES.GLOBAL_SETTINGS });
        turGlobalSettingsService
            .query()
            .then((settings) => {
                form.reset(settings);
            })
            .catch(() => {
                toast.error("Failed to load global settings. Using default values.");
            })
            .finally(() => setIsLoading(false));

        return () => popItem();
    }, [form, popItem, pushItem]);

    const onSubmit = async (settings: TurGlobalSettings) => {
        try {
            const response = await turGlobalSettingsService.update(settings);
            form.reset(response);
            toast.success("Global settings were updated.");
        } catch (error) {
            console.error("Form submission error", error);
            toast.error("Failed to update global settings.");
        }
    };

    const decimalSeparator = form.watch("decimalSeparator") ?? "DOT";
    const decimalExample = decimalSeparator === "COMMA" ? "150,75" : "150.75";
    const currencyExample =
        decimalSeparator === "COMMA" ? "150,75,BRL" : "150.75,BRL";

    return (
        <>
            <SubPageHeader
                icon={IconSettings}
                feature="Global Settings"
                name="Global Settings"
                description="System-wide administration settings."
            />
            <div className="px-6 py-8 max-w-xl">
                <Form {...form}>
                    <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-6 border rounded-lg p-6">
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
                                            <SelectTrigger className="w-full">
                                                <SelectValue placeholder="Choose..." />
                                            </SelectTrigger>
                                            <SelectContent>
                                                <SelectItem value="DOT">Dot (.)</SelectItem>
                                                <SelectItem value="COMMA">Comma (,)</SelectItem>
                                            </SelectContent>
                                        </Select>
                                    </FormControl>
                                    <FormDescription>
                                        Decimal example: <strong>{decimalExample}</strong> | Currency example: <strong>{currencyExample}</strong>
                                    </FormDescription>
                                    <FormMessage />
                                </FormItem>
                            )}
                        />

                        <FormField
                            control={form.control}
                            name="pythonExecutable"
                            render={({ field }) => (
                                <FormItem>
                                    <FormLabel>Python Executable Path</FormLabel>
                                    <FormDescription>
                                        Absolute path to the Python interpreter used by the code interpreter tool (e.g. <code>/usr/bin/python3</code>).
                                        Leave empty to auto-detect from known system locations.
                                    </FormDescription>
                                    <FormControl>
                                        <Input
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

                        <GradientButton type="submit" disabled={isLoading}>
                            Save
                        </GradientButton>
                    </form>
                </Form>
            </div>
        </>
    );
}
