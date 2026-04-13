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
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { StickyPageHeader } from "@/components/sticky-page-header";
import type { TurGlobalSettings } from "@/models/system/global-settings.model";
import { TurGlobalSettingsService } from "@/services/system/global-settings.service";
import { SectionCard } from "@/components/ui/section-card";
import { IconBrain, IconDatabase, IconLoader2, IconMail, IconMailForward, IconSettings } from "@tabler/icons-react";
import type { TurLLMInstance } from "@/models/llm/llm-instance.model";
import type { TurEmbeddingModel } from "@/models/embedding/embedding-model.model";
import type { TurStoreInstance } from "@/models/store/store-instance.model";
import { TurLLMInstanceService } from "@/services/llm/llm.service";
import { TurEmbeddingModelService } from "@/services/embedding/embedding-model.service";
import { TurStoreInstanceService } from "@/services/store/store.service";
import { GradientButton } from "@/components/ui/gradient-button";
import { GradientSwitch } from "@/components/ui/gradient-switch";
import { DurationInput } from "@/components/ui/duration-input";
import { useSubPageBreadcrumb } from "@/hooks/use-sub-page-breadcrumb";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { toast } from "@viglet/viglet-design-system";

const turGlobalSettingsService = new TurGlobalSettingsService();
const turLLMInstanceService = new TurLLMInstanceService();
const turEmbeddingModelService = new TurEmbeddingModelService();
const turStoreInstanceService = new TurStoreInstanceService();

const DEFAULT_SETTINGS: TurGlobalSettings = {
    decimalSeparator: "DOT",
    defaultLlmId: "",
    pythonExecutable: "",
    llmCacheEnabled: false,
    llmCacheTtlMs: 3600000,
    llmCacheRegenerate: false,
    emailProvider: "BREVO",
    emailApiKey: "",
    senderEmail: "",
    senderName: "",
    recipientEmail: "",
    ragEnabled: false,
    defaultEmbeddingModelId: "",
    defaultEmbeddingStoreId: "",
};

export default function GlobalSettingsPage() {
    const { t } = useTranslation();
    useSubPageBreadcrumb(t("globalSettings.breadcrumb"));

    const [settings, setSettings] = useState<TurGlobalSettings>(DEFAULT_SETTINGS);
    const form = useForm<TurGlobalSettings>({
        defaultValues: DEFAULT_SETTINGS,
        values: settings,
    });
    const [isLoading, setIsLoading] = useState(true);
    const [isSaving, setIsSaving] = useState(false);
    const [isSendingTest, setIsSendingTest] = useState(false);
    const [llmInstances, setLlmInstances] = useState<TurLLMInstance[]>([]);
    const [embeddingModels, setEmbeddingModels] = useState<TurEmbeddingModel[]>([]);
    const [storeInstances, setStoreInstances] = useState<TurStoreInstance[]>([]);

    useEffect(() => {
        Promise.all([
            turGlobalSettingsService.query(),
            turLLMInstanceService.query(),
            turEmbeddingModelService.query(),
            turStoreInstanceService.query(),
        ])
            .then(([loadedSettings, instances, embModels, stores]) => {
                setLlmInstances(instances.filter((i) => i.enabled === 1));
                setEmbeddingModels(embModels.filter((m) => m.enabled === 1));
                setStoreInstances(stores.filter((s) => s.enabled === 1));
                setSettings(loadedSettings);
            })
            .catch(() => {
                toast.error(t("globalSettings.loadFailed"));
            })
            .finally(() => setIsLoading(false));
    }, []);

    const onSubmit = async (data: TurGlobalSettings) => {
        try {
            setIsSaving(true);
            const response = await turGlobalSettingsService.update(data);
            setSettings(response);
            toast.success(t("globalSettings.updateSuccess"));
        } catch (error) {
            console.error("Form submission error", error);
            toast.error(t("globalSettings.updateFailed"));
        } finally {
            setIsSaving(false);
        }
    };

    const onSendTestEmail = async () => {
        try {
            setIsSendingTest(true);
            const result = await turGlobalSettingsService.sendTestEmail();
            if (result.success) {
                toast.success(result.message);
            } else {
                toast.error(result.message);
            }
        } catch {
            toast.error(t("globalSettings.testEmailFailed"));
        } finally {
            setIsSendingTest(false);
        }
    };

    const decimalSeparator = form.watch("decimalSeparator") ?? "DOT";
    const decimalExample = decimalSeparator === "COMMA" ? "1.500,75" : "1,500.75";
    const currencyExample =
        decimalSeparator === "COMMA" ? "150,75,BRL" : "150.75,BRL";

    const llmCacheEnabled = form.watch("llmCacheEnabled") ?? false;
    const ragEnabled = form.watch("ragEnabled") ?? false;

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
                <StickyPageHeader
                    icon={IconSettings}
                    feature={t("globalSettings.breadcrumb")}
                    description={t("globalSettings.description")}
                    showSave
                    loading={isSaving}
                    disabled={isLoading}
                />
                    <SectionCard variant="amber">
                        <SectionCard.Header icon={IconSettings} title={t("globalSettings.sectionTitle")} description={t("globalSettings.sectionDescription")} />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="decimalSeparator"
                                render={({ field }) => (
                                    <FormItemTwoColumns>
                                        <FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Label>{t("globalSettings.decimalSeparator")}</FormItemTwoColumns.Label>
                                            <FormItemTwoColumns.Description>
                                                {t("globalSettings.decimalSeparatorDesc")}
                                            </FormItemTwoColumns.Description>
                                        </FormItemTwoColumns.Left>
                                        <FormItemTwoColumns.Right>
                                            <FormControl>
                                                <Select onValueChange={field.onChange} value={field.value} disabled={isLoading}>
                                                    <SelectTrigger className="w-full max-w-xs">
                                                        <SelectValue placeholder={t("globalSettings.choose")} />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value="DOT">{t("globalSettings.dot")}</SelectItem>
                                                        <SelectItem value="COMMA">{t("globalSettings.comma")}</SelectItem>
                                                    </SelectContent>
                                                </Select>
                                            </FormControl>
                                        </FormItemTwoColumns.Right>
                                    </FormItemTwoColumns>
                                )}
                            />
                            <div className="flex gap-4">
                                <div className="flex-1 rounded-lg bg-muted/40 px-4 py-3">
                                    <span className="text-xs text-muted-foreground">{t("globalSettings.decimalPreview")}</span>
                                    <p className="text-sm font-mono font-semibold mt-0.5">{decimalExample}</p>
                                </div>
                                <div className="flex-1 rounded-lg bg-muted/40 px-4 py-3">
                                    <span className="text-xs text-muted-foreground">{t("globalSettings.currencyFormat")}</span>
                                    <p className="text-sm font-mono font-semibold mt-0.5">{currencyExample}</p>
                                </div>
                            </div>
                            <FormField
                                control={form.control}
                                name="pythonExecutable"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("globalSettings.pythonPath")}</FormLabel>
                                        <FormDescription>
                                            {t("globalSettings.pythonPathDesc")}
                                        </FormDescription>
                                        <FormControl>
                                            <Input
                                                className="max-w-md font-mono text-sm"
                                                placeholder="/usr/bin/python3"
                                                autoComplete="one-time-code"
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

                    <SectionCard variant="violet">
                        <SectionCard.Header icon={IconBrain} title={t("globalSettings.llmSettings")} description={t("globalSettings.llmSettingsDesc")} />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="defaultLlmId"
                                render={({ field }) => (
                                    <FormItemTwoColumns>
                                        <FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Label>{t("globalSettings.defaultLlm")}</FormItemTwoColumns.Label>
                                            <FormItemTwoColumns.Description>
                                                {t("globalSettings.defaultLlmDesc")}
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
                                                        <SelectValue placeholder={t("globalSettings.selectLlm")} />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value="none">{t("globalSettings.none")}</SelectItem>
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
                            <FormField
                                control={form.control}
                                name="llmCacheEnabled"
                                render={({ field }) => (
                                    <FormItemTwoColumns>
                                        <FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Label>{t("globalSettings.llmCache")}</FormItemTwoColumns.Label>
                                            <FormItemTwoColumns.Description>
                                                {t("globalSettings.llmCacheDesc")}
                                            </FormItemTwoColumns.Description>
                                        </FormItemTwoColumns.Left>
                                        <FormItemTwoColumns.Right>
                                            <FormControl>
                                                <GradientSwitch
                                                    checked={field.value ?? false}
                                                    onCheckedChange={field.onChange}
                                                    disabled={isLoading}
                                                />
                                            </FormControl>
                                        </FormItemTwoColumns.Right>
                                    </FormItemTwoColumns>
                                )}
                            />
                            {llmCacheEnabled && (
                                <>
                                    <FormField
                                        control={form.control}
                                        name="llmCacheTtlMs"
                                        render={({ field }) => (
                                            <FormItem>
                                                <FormLabel>{t("globalSettings.cacheDuration")}</FormLabel>
                                                <FormDescription>
                                                    {t("globalSettings.cacheDurationDesc")}
                                                </FormDescription>
                                                <FormControl>
                                                    <DurationInput
                                                        value={field.value ?? 3600000}
                                                        onChange={field.onChange}
                                                        disabled={isLoading}
                                                    />
                                                </FormControl>
                                                <FormMessage />
                                            </FormItem>
                                        )}
                                    />
                                    <FormField
                                        control={form.control}
                                        name="llmCacheRegenerate"
                                        render={({ field }) => (
                                            <FormItemTwoColumns>
                                                <FormItemTwoColumns.Left>
                                                    <FormItemTwoColumns.Label>{t("globalSettings.regenerateCache")}</FormItemTwoColumns.Label>
                                                    <FormItemTwoColumns.Description>
                                                        {t("globalSettings.regenerateCacheDesc")}
                                                    </FormItemTwoColumns.Description>
                                                </FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Right>
                                                    <FormControl>
                                                        <GradientSwitch
                                                            checked={field.value ?? false}
                                                            onCheckedChange={field.onChange}
                                                            disabled={isLoading}
                                                        />
                                                    </FormControl>
                                                </FormItemTwoColumns.Right>
                                            </FormItemTwoColumns>
                                        )}
                                    />
                                </>
                            )}
                        </SectionCard.Content>
                    </SectionCard>

                    <SectionCard variant="emerald">
                        <SectionCard.Header icon={IconDatabase} title={t("globalSettings.ragSettings")} description={t("globalSettings.ragSettingsDesc")} />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="ragEnabled"
                                render={({ field }) => (
                                    <FormItemTwoColumns>
                                        <FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Label>{t("globalSettings.enableRag")}</FormItemTwoColumns.Label>
                                            <FormItemTwoColumns.Description>
                                                {t("globalSettings.enableRagDesc")}
                                            </FormItemTwoColumns.Description>
                                        </FormItemTwoColumns.Left>
                                        <FormItemTwoColumns.Right>
                                            <FormControl>
                                                <GradientSwitch
                                                    checked={field.value ?? false}
                                                    onCheckedChange={field.onChange}
                                                    disabled={isLoading}
                                                />
                                            </FormControl>
                                        </FormItemTwoColumns.Right>
                                    </FormItemTwoColumns>
                                )}
                            />
                            {ragEnabled && (
                                <>
                                    <FormField
                                        control={form.control}
                                        name="defaultEmbeddingModelId"
                                        rules={{ required: ragEnabled ? t("globalSettings.embeddingModelRequired") : false }}
                                        render={({ field }) => (
                                            <FormItemTwoColumns>
                                                <FormItemTwoColumns.Left>
                                                    <FormItemTwoColumns.Label>{t("globalSettings.defaultEmbeddingModel")}</FormItemTwoColumns.Label>
                                                    <FormItemTwoColumns.Description>
                                                        {t("globalSettings.defaultEmbeddingModelDesc")}
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
                                                                <SelectValue placeholder={t("globalSettings.selectEmbeddingModel")} />
                                                            </SelectTrigger>
                                                            <SelectContent>
                                                                <SelectItem value="none">{t("globalSettings.none")}</SelectItem>
                                                                {embeddingModels.map((model) => (
                                                                    <SelectItem key={model.id} value={model.id}>
                                                                        {model.modelName}
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
                                    <FormField
                                        control={form.control}
                                        name="defaultEmbeddingStoreId"
                                        rules={{ required: ragEnabled ? t("globalSettings.embeddingStoreRequired") : false }}
                                        render={({ field }) => (
                                            <FormItemTwoColumns>
                                                <FormItemTwoColumns.Left>
                                                    <FormItemTwoColumns.Label>{t("globalSettings.defaultEmbeddingStore")}</FormItemTwoColumns.Label>
                                                    <FormItemTwoColumns.Description>
                                                        {t("globalSettings.defaultEmbeddingStoreDesc")}
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
                                                                <SelectValue placeholder={t("globalSettings.selectEmbeddingStore")} />
                                                            </SelectTrigger>
                                                            <SelectContent>
                                                                <SelectItem value="none">{t("globalSettings.none")}</SelectItem>
                                                                {storeInstances.map((store) => (
                                                                    <SelectItem key={store.id} value={store.id}>
                                                                        {store.title}
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
                                </>
                            )}
                        </SectionCard.Content>
                    </SectionCard>

                    <SectionCard variant="amber">
                        <SectionCard.Header icon={IconMail} title={t("globalSettings.emailSettings")} description={t("globalSettings.emailSettingsDesc")} />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="emailProvider"
                                render={({ field }) => (
                                    <FormItemTwoColumns>
                                        <FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Label>{t("globalSettings.emailProvider")}</FormItemTwoColumns.Label>
                                            <FormItemTwoColumns.Description>
                                                {t("globalSettings.emailProviderDesc")}
                                            </FormItemTwoColumns.Description>
                                        </FormItemTwoColumns.Left>
                                        <FormItemTwoColumns.Right>
                                            <FormControl>
                                                <Select onValueChange={field.onChange} value={field.value ?? "BREVO"} disabled={isLoading}>
                                                    <SelectTrigger className="w-full max-w-xs">
                                                        <SelectValue placeholder={t("globalSettings.choose")} />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value="BREVO">{t("globalSettings.brevo")}</SelectItem>
                                                    </SelectContent>
                                                </Select>
                                            </FormControl>
                                        </FormItemTwoColumns.Right>
                                    </FormItemTwoColumns>
                                )}
                            />
                            <FormField
                                control={form.control}
                                name="emailApiKey"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("globalSettings.apiKey")}</FormLabel>
                                        <FormDescription>
                                            {t("globalSettings.apiKeyDesc")}
                                        </FormDescription>
                                        <FormControl>
                                            <Input
                                                type="password"
                                                className="max-w-md font-mono text-sm"
                                                placeholder="xkeysib-..."
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
                                name="senderEmail"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("globalSettings.senderEmail")}</FormLabel>
                                        <FormDescription>
                                            {t("globalSettings.senderEmailDesc")}
                                        </FormDescription>
                                        <FormControl>
                                            <Input
                                                type="email"
                                                className="max-w-md text-sm"
                                                placeholder="noreply@example.com"
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
                                name="senderName"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("globalSettings.senderName")}</FormLabel>
                                        <FormDescription>
                                            {t("globalSettings.senderNameDesc")}
                                        </FormDescription>
                                        <FormControl>
                                            <Input
                                                className="max-w-md text-sm"
                                                placeholder="Viglet Turing"
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
                                name="recipientEmail"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("globalSettings.recipientEmail")}</FormLabel>
                                        <FormDescription>
                                            {t("globalSettings.recipientEmailDesc")}
                                        </FormDescription>
                                        <FormControl>
                                            <Input
                                                type="email"
                                                className="max-w-md text-sm"
                                                placeholder="admin@example.com"
                                                disabled={isLoading}
                                                {...field}
                                                value={field.value ?? ""}
                                            />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />
                            <div className="flex items-center gap-3 pt-4 border-t">
                                <GradientButton
                                    type="button"
                                    variant="outline"
                                    disabled={isLoading || isSendingTest}
                                    onClick={onSendTestEmail}
                                >
                                    {isSendingTest ? (
                                        <IconLoader2 className="size-4 animate-spin" />
                                    ) : (
                                        <IconMailForward className="size-4" />
                                    )}
                                    {isSendingTest ? t("globalSettings.sending") : t("globalSettings.sendTestEmail")}
                                </GradientButton>
                                <span className="text-xs text-muted-foreground">
                                    {t("globalSettings.testEmailHint")}
                                </span>
                            </div>
                        </SectionCard.Content>
                    </SectionCard>

            </form>
        </Form>
    );
}
