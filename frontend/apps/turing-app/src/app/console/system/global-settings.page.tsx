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
import { Textarea } from "@/components/ui/textarea";
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
import { IconAlertTriangle, IconArrowsSort, IconBrain, IconBrandDocker, IconCheck, IconDatabase, IconDeviceFloppy, IconKey, IconLoader2, IconMail, IconMailForward, IconRefresh, IconSettings } from "@tabler/icons-react";
import type { TurLLMInstance } from "@/models/llm/llm-instance.model";
import type { TurEmbeddingModel } from "@/models/embedding/embedding-model.model";
import type { TurStoreInstance } from "@/models/store/store-instance.model";
import type { TurAIAgent } from "@/models/agent/ai-agent.model";
import { TurLLMInstanceService } from "@/services/llm/llm.service";
import { TurEmbeddingModelService } from "@/services/embedding/embedding-model.service";
import { TurStoreInstanceService } from "@/services/store/store.service";
import { TurAIAgentService } from "@/services/agent/ai-agent.service";
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
const turAIAgentService = new TurAIAgentService();

const DEFAULT_SETTINGS: TurGlobalSettings = {
    decimalSeparator: "DOT",
    defaultLlmId: "",
    pythonExecutable: "",
    pythonRequirements: "",
    codeInterpreterExecutionMode: "NATIVE",
    codeInterpreterDockerImage: "python:3.12-slim",
    codeInterpreterSkillImage: "python:3.12-slim",
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
    defaultAiAgentId: "",
    piiSlotTtlHours: 24,
    ragSnRerankEnabled: false,
    ragSnRerankTopN: 20,
    ragSnRerankStrategy: "LLM",
    ragSnRerankEndpoint: "",
    ragSnRerankModel: "",
    ragSnRerankApiKey: "",
    ragSnRerankApiKeySet: false,
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
    // HMAC URL-signing secret status. The value itself never reaches this
    // page — only a boolean for "configured/blank" and a short preview
    // after a rotation. Screenshot-safe by construction.
    const [secretConfigured, setSecretConfigured] = useState<boolean | null>(null);
    const [secretPreview, setSecretPreview] = useState<string | null>(null);
    const [isRotatingSecret, setIsRotatingSecret] = useState(false);
    // T80 — Docker availability probe for the Code Interpreter sandbox.
    // Only meaningful when the execution mode is DOCKER; lets the admin
    // confirm the host can run containers before saving.
    const [dockerStatus, setDockerStatus] = useState<{
        available: boolean;
        serverVersion: string | null;
        error: string | null;
    } | null>(null);
    const [isCheckingDocker, setIsCheckingDocker] = useState(false);
    const [llmInstances, setLlmInstances] = useState<TurLLMInstance[]>([]);
    const [embeddingModels, setEmbeddingModels] = useState<TurEmbeddingModel[]>([]);
    const [storeInstances, setStoreInstances] = useState<TurStoreInstance[]>([]);
    const [aiAgents, setAiAgents] = useState<TurAIAgent[]>([]);

    useEffect(() => {
        Promise.all([
            turGlobalSettingsService.query(),
            turLLMInstanceService.query(),
            turEmbeddingModelService.query(),
            turStoreInstanceService.query(),
            turAIAgentService.query(),
            // URL-signing secret status (boolean only — value stays on server).
            // Fail-soft: if this 6th call errors, we still load the rest of the
            // page and just show "unknown" status; admin can still hit the
            // regenerate button which mints a fresh row.
            turGlobalSettingsService.getUrlSigningSecretStatus().catch(() => ({ configured: false })),
        ])
            .then(([loadedSettings, instances, embModels, stores, agents, secretStatus]) => {
                setLlmInstances(instances.filter((i) => i.enabled === 1));
                setEmbeddingModels(embModels.filter((m) => m.enabled === 1));
                setStoreInstances(stores.filter((s) => s.enabled === 1));
                setAiAgents(agents.filter((a) => a.enabled === 1));
                setSettings(loadedSettings);
                setSecretConfigured(secretStatus.configured);
            })
            .catch(() => {
                toast.error(t("globalSettings.loadFailed"));
            })
            .finally(() => setIsLoading(false));
    }, []);

    const onRotateUrlSigningSecret = async () => {
        // Confirm in a native dialog — rotation invalidates every previously
        // emitted signed URL across the fleet. No-op when user cancels.
        const confirmed = window.confirm(t("globalSettings.urlSigningSecretRotateConfirm"));
        if (!confirmed) return;
        try {
            setIsRotatingSecret(true);
            const result = await turGlobalSettingsService.regenerateUrlSigningSecret();
            setSecretConfigured(true);
            setSecretPreview(result.preview);
            toast.success(t("globalSettings.urlSigningSecretRotateSuccess"));
        } catch (error) {
            console.error("rotate url signing secret failed", error);
            toast.error(t("globalSettings.urlSigningSecretRotateFailed"));
        } finally {
            setIsRotatingSecret(false);
        }
    };

    const onCheckDocker = async () => {
        try {
            setIsCheckingDocker(true);
            const status = await turGlobalSettingsService.getDockerStatus();
            setDockerStatus(status);
            if (status.available) {
                toast.success(t("globalSettings.dockerCheckSuccess", { version: status.serverVersion }));
            } else {
                toast.error(t("globalSettings.dockerCheckFailed", { error: status.error ?? "" }));
            }
        } catch (error) {
            console.error("docker status check failed", error);
            setDockerStatus({ available: false, serverVersion: null, error: "request failed" });
            toast.error(t("globalSettings.dockerCheckFailed", { error: "" }));
        } finally {
            setIsCheckingDocker(false);
        }
    };

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
    const codeInterpreterMode = form.watch("codeInterpreterExecutionMode") ?? "NATIVE";
    const rerankEnabled = form.watch("ragSnRerankEnabled") ?? false;
    const rerankStrategy = form.watch("ragSnRerankStrategy") ?? "LLM";
    const rerankApiKeySet = settings.ragSnRerankApiKeySet ?? false;

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
                <StickyPageHeader>
                    <StickyPageHeader.Title
                        icon={IconSettings}
                        feature={t("globalSettings.breadcrumb")}
                        description={t("globalSettings.description")}
                    />
                    <StickyPageHeader.Actions>
                        <GradientButton type="submit" size="sm" loading={isSaving} disabled={isLoading}>
                            <IconDeviceFloppy className="size-4" />
                            {t("forms.formActions.saveChanges")}
                        </GradientButton>
                    </StickyPageHeader.Actions>
                </StickyPageHeader>
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
                            <FormField
                                control={form.control}
                                name="pythonRequirements"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("globalSettings.pythonRequirements")}</FormLabel>
                                        <FormDescription>
                                            {t("globalSettings.pythonRequirementsDesc")}
                                        </FormDescription>
                                        <FormControl>
                                            <Textarea
                                                className="max-w-md font-mono text-sm"
                                                rows={5}
                                                placeholder={"reportlab==4.0.7\nqrcode>=7.4\nmatplotlib"}
                                                spellCheck={false}
                                                autoComplete="off"
                                                disabled={isLoading}
                                                {...field}
                                                value={field.value ?? ""}
                                            />
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />

                            {/* T80 — execution mode. NATIVE keeps the legacy
                                host-subprocess behavior; DOCKER runs each
                                execution in a throwaway hardened container.
                                Operator choice — switching needs no redeploy. */}
                            <FormField
                                control={form.control}
                                name="codeInterpreterExecutionMode"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("globalSettings.codeInterpreterMode")}</FormLabel>
                                        <FormDescription>
                                            {t("globalSettings.codeInterpreterModeDesc")}
                                        </FormDescription>
                                        <FormControl>
                                            <Select
                                                onValueChange={field.onChange}
                                                value={field.value ?? "NATIVE"}
                                                disabled={isLoading}
                                            >
                                                <SelectTrigger className="w-full max-w-xs">
                                                    <SelectValue placeholder={t("globalSettings.choose")} />
                                                </SelectTrigger>
                                                <SelectContent>
                                                    <SelectItem value="NATIVE">{t("globalSettings.codeInterpreterModeNative")}</SelectItem>
                                                    <SelectItem value="DOCKER">{t("globalSettings.codeInterpreterModeDocker")}</SelectItem>
                                                </SelectContent>
                                            </Select>
                                        </FormControl>
                                        <FormMessage />
                                    </FormItem>
                                )}
                            />

                            {/* Docker-only options — image + availability probe.
                                Shown only when the mode is DOCKER to keep the
                                NATIVE form uncluttered. */}
                            {codeInterpreterMode === "DOCKER" && (
                                <>
                                    <FormField
                                        control={form.control}
                                        name="codeInterpreterDockerImage"
                                        render={({ field }) => (
                                            <FormItem>
                                                <FormLabel>{t("globalSettings.codeInterpreterImage")}</FormLabel>
                                                <FormDescription>
                                                    {t("globalSettings.codeInterpreterImageDesc")}
                                                </FormDescription>
                                                <FormControl>
                                                    <Input
                                                        className="max-w-md font-mono text-sm"
                                                        placeholder="python:3.12-slim"
                                                        autoComplete="off"
                                                        spellCheck={false}
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
                                        name="codeInterpreterSkillImage"
                                        render={({ field }) => (
                                            <FormItem>
                                                <FormLabel>{t("globalSettings.codeInterpreterSkillImage")}</FormLabel>
                                                <FormDescription>
                                                    {t("globalSettings.codeInterpreterSkillImageDesc")}
                                                </FormDescription>
                                                <FormControl>
                                                    <Input
                                                        className="max-w-md font-mono text-sm"
                                                        placeholder="python:3.12-slim"
                                                        autoComplete="off"
                                                        spellCheck={false}
                                                        disabled={isLoading}
                                                        {...field}
                                                        value={field.value ?? ""}
                                                    />
                                                </FormControl>
                                                <FormMessage />
                                            </FormItem>
                                        )}
                                    />
                                    <div className="flex flex-col gap-2">
                                        <div className="flex items-center gap-2">
                                            <GradientButton
                                                type="button"
                                                variant="outline"
                                                size="sm"
                                                onClick={onCheckDocker}
                                                loading={isCheckingDocker}
                                                disabled={isLoading || isCheckingDocker}
                                            >
                                                <IconBrandDocker className="size-4" />
                                                {t("globalSettings.dockerCheckAction")}
                                            </GradientButton>
                                            {dockerStatus?.available && (
                                                <span className="inline-flex items-center gap-1 text-xs text-emerald-600 dark:text-emerald-400">
                                                    <IconCheck className="size-4" />
                                                    {t("globalSettings.dockerStatusAvailable", { version: dockerStatus.serverVersion })}
                                                </span>
                                            )}
                                            {dockerStatus && !dockerStatus.available && (
                                                <span className="inline-flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400">
                                                    <IconAlertTriangle className="size-4" />
                                                    {t("globalSettings.dockerStatusUnavailable")}
                                                </span>
                                            )}
                                        </div>
                                        {dockerStatus && !dockerStatus.available && dockerStatus.error && (
                                            <p className="font-mono text-xs text-muted-foreground break-all max-w-md">
                                                {dockerStatus.error}
                                            </p>
                                        )}
                                    </div>
                                </>
                            )}
                        </SectionCard.Content>
                    </SectionCard>

                    {/* URL Signing Secret — Code Interpreter file API HMAC.
                        Status-only (no value on the wire); regenerate button
                        mints a fresh secret server-side. Rotating invalidates
                        every previously emitted signed download URL — that's
                        the intended behavior of an operator-initiated rotation. */}
                    <SectionCard variant="rose">
                        <SectionCard.Header
                            icon={IconKey}
                            title={t("globalSettings.urlSigningSecretTitle")}
                            description={t("globalSettings.urlSigningSecretDesc")}
                        />
                        <SectionCard.Content>
                            <FormItemTwoColumns>
                                <FormItemTwoColumns.Left>
                                    <FormItemTwoColumns.Label>
                                        {t("globalSettings.urlSigningSecretStatusLabel")}
                                    </FormItemTwoColumns.Label>
                                    <FormItemTwoColumns.Description>
                                        {secretConfigured === null
                                            ? t("globalSettings.urlSigningSecretStatusUnknown")
                                            : secretConfigured
                                                ? t("globalSettings.urlSigningSecretStatusConfigured")
                                                : t("globalSettings.urlSigningSecretStatusBlank")}
                                        {secretPreview && (
                                            <span className="block mt-1 font-mono text-xs text-muted-foreground">
                                                {t("globalSettings.urlSigningSecretPreview", { preview: secretPreview })}
                                            </span>
                                        )}
                                    </FormItemTwoColumns.Description>
                                </FormItemTwoColumns.Left>
                                <FormItemTwoColumns.Right>
                                    <div className="flex items-center gap-2">
                                        {secretConfigured && !isRotatingSecret && (
                                            <span className="inline-flex items-center gap-1 text-xs text-emerald-600 dark:text-emerald-400">
                                                <IconCheck className="size-4" />
                                                {t("globalSettings.urlSigningSecretSetBadge")}
                                            </span>
                                        )}
                                        {!secretConfigured && secretConfigured !== null && (
                                            <span className="inline-flex items-center gap-1 text-xs text-amber-600 dark:text-amber-400">
                                                <IconAlertTriangle className="size-4" />
                                                {t("globalSettings.urlSigningSecretBlankBadge")}
                                            </span>
                                        )}
                                        <GradientButton
                                            type="button"
                                            variant="outline"
                                            size="sm"
                                            onClick={onRotateUrlSigningSecret}
                                            loading={isRotatingSecret}
                                            disabled={isLoading || isRotatingSecret}
                                        >
                                            <IconRefresh className="size-4" />
                                            {t("globalSettings.urlSigningSecretRotateAction")}
                                        </GradientButton>
                                    </div>
                                </FormItemTwoColumns.Right>
                            </FormItemTwoColumns>
                        </SectionCard.Content>
                    </SectionCard>

                    {/* T61 — PII slot retention TTL. Hourly cleanup removes
                        `pii_*` slot values older than the configured horizon.
                        Encryption-at-rest + log redaction always apply
                        regardless of the TTL value. */}
                    <SectionCard variant="amber">
                        <SectionCard.Header
                            icon={IconAlertTriangle}
                            title={t("globalSettings.piiSlotTitle")}
                            description={t("globalSettings.piiSlotDesc")}
                        />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="piiSlotTtlHours"
                                rules={{
                                    min: { value: 0, message: t("globalSettings.piiSlotTtlOutOfRange") },
                                    max: { value: 8760, message: t("globalSettings.piiSlotTtlOutOfRange") },
                                }}
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("globalSettings.piiSlotTtlLabel")}</FormLabel>
                                        <FormDescription>
                                            {t("globalSettings.piiSlotTtlDesc")}
                                        </FormDescription>
                                        <FormControl>
                                            <Input
                                                type="number"
                                                min={0}
                                                max={8760}
                                                step={1}
                                                disabled={isLoading}
                                                {...field}
                                                value={field.value ?? 0}
                                                onChange={e => field.onChange(Number(e.target.value))}
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
                                name="defaultAiAgentId"
                                render={({ field }) => (
                                    <FormItemTwoColumns>
                                        <FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Label>{t("globalSettings.defaultAiAgent")}</FormItemTwoColumns.Label>
                                            <FormItemTwoColumns.Description>
                                                {t("globalSettings.defaultAiAgentDesc")}
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
                                                        <SelectValue placeholder={t("globalSettings.selectAiAgent")} />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value="none">{t("globalSettings.none")}</SelectItem>
                                                        {aiAgents.map((agent) => (
                                                            <SelectItem key={agent.id} value={agent.id}>
                                                                {agent.title}
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

                    {/* T337–T339 — SN RAG reranker. Opt-in (default off); the
                        backend defaults to the legacy LLM strategy. CROSS_ENCODER
                        and COHERE reveal their own connection fields. */}
                    <SectionCard variant="emerald">
                        <SectionCard.Header icon={IconArrowsSort} title={t("globalSettings.rerankTitle")} description={t("globalSettings.rerankDesc")} />
                        <SectionCard.Content>
                            <FormField
                                control={form.control}
                                name="ragSnRerankEnabled"
                                render={({ field }) => (
                                    <FormItemTwoColumns>
                                        <FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Label>{t("globalSettings.rerankEnable")}</FormItemTwoColumns.Label>
                                            <FormItemTwoColumns.Description>
                                                {t("globalSettings.rerankEnableDesc")}
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
                            {rerankEnabled && (
                                <>
                                    <FormField
                                        control={form.control}
                                        name="ragSnRerankStrategy"
                                        render={({ field }) => (
                                            <FormItemTwoColumns>
                                                <FormItemTwoColumns.Left>
                                                    <FormItemTwoColumns.Label>{t("globalSettings.rerankStrategy")}</FormItemTwoColumns.Label>
                                                    <FormItemTwoColumns.Description>
                                                        {t("globalSettings.rerankStrategyDesc")}
                                                    </FormItemTwoColumns.Description>
                                                </FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Right>
                                                    <FormControl>
                                                        <Select
                                                            onValueChange={field.onChange}
                                                            value={field.value ?? "LLM"}
                                                            disabled={isLoading}
                                                        >
                                                            <SelectTrigger className="w-full max-w-xs">
                                                                <SelectValue placeholder={t("globalSettings.choose")} />
                                                            </SelectTrigger>
                                                            <SelectContent>
                                                                <SelectItem value="LLM">{t("globalSettings.rerankStrategyLlm")}</SelectItem>
                                                                <SelectItem value="CROSS_ENCODER">{t("globalSettings.rerankStrategyCrossEncoder")}</SelectItem>
                                                                <SelectItem value="COHERE">{t("globalSettings.rerankStrategyCohere")}</SelectItem>
                                                            </SelectContent>
                                                        </Select>
                                                    </FormControl>
                                                </FormItemTwoColumns.Right>
                                            </FormItemTwoColumns>
                                        )}
                                    />
                                    <FormField
                                        control={form.control}
                                        name="ragSnRerankTopN"
                                        rules={{
                                            min: { value: 1, message: t("globalSettings.rerankTopNOutOfRange") },
                                            max: { value: 100, message: t("globalSettings.rerankTopNOutOfRange") },
                                        }}
                                        render={({ field }) => (
                                            <FormItem>
                                                <FormLabel>{t("globalSettings.rerankTopN")}</FormLabel>
                                                <FormDescription>
                                                    {t("globalSettings.rerankTopNDesc")}
                                                </FormDescription>
                                                <FormControl>
                                                    <Input
                                                        type="number"
                                                        min={1}
                                                        max={100}
                                                        step={1}
                                                        className="max-w-xs"
                                                        disabled={isLoading}
                                                        {...field}
                                                        value={field.value ?? 20}
                                                        onChange={e => field.onChange(Number(e.target.value))}
                                                    />
                                                </FormControl>
                                                <FormMessage />
                                            </FormItem>
                                        )}
                                    />
                                    {(rerankStrategy === "CROSS_ENCODER" || rerankStrategy === "COHERE") && (
                                        <FormField
                                            control={form.control}
                                            name="ragSnRerankModel"
                                            render={({ field }) => (
                                                <FormItem>
                                                    <FormLabel>{t("globalSettings.rerankModel")}</FormLabel>
                                                    <FormDescription>
                                                        {t("globalSettings.rerankModelDesc")}
                                                    </FormDescription>
                                                    <FormControl>
                                                        <Input
                                                            className="max-w-md font-mono text-sm"
                                                            placeholder={rerankStrategy === "COHERE" ? "rerank-v3.5" : "BAAI/bge-reranker-v2-m3"}
                                                            autoComplete="off"
                                                            spellCheck={false}
                                                            disabled={isLoading}
                                                            {...field}
                                                            value={field.value ?? ""}
                                                        />
                                                    </FormControl>
                                                    <FormMessage />
                                                </FormItem>
                                            )}
                                        />
                                    )}
                                    {rerankStrategy === "CROSS_ENCODER" && (
                                        <FormField
                                            control={form.control}
                                            name="ragSnRerankEndpoint"
                                            render={({ field }) => (
                                                <FormItem>
                                                    <FormLabel>{t("globalSettings.rerankEndpoint")}</FormLabel>
                                                    <FormDescription>
                                                        {t("globalSettings.rerankEndpointDesc")}
                                                    </FormDescription>
                                                    <FormControl>
                                                        <Input
                                                            className="max-w-md font-mono text-sm"
                                                            placeholder="http://localhost:8080/rerank"
                                                            autoComplete="off"
                                                            spellCheck={false}
                                                            disabled={isLoading}
                                                            {...field}
                                                            value={field.value ?? ""}
                                                        />
                                                    </FormControl>
                                                    <FormMessage />
                                                </FormItem>
                                            )}
                                        />
                                    )}
                                    {rerankStrategy === "COHERE" && (
                                        <FormField
                                            control={form.control}
                                            name="ragSnRerankApiKey"
                                            render={({ field }) => (
                                                <FormItem>
                                                    <FormLabel>{t("globalSettings.rerankApiKey")}</FormLabel>
                                                    <FormDescription>
                                                        {rerankApiKeySet
                                                            ? t("globalSettings.rerankApiKeySetDesc")
                                                            : t("globalSettings.rerankApiKeyDesc")}
                                                    </FormDescription>
                                                    <FormControl>
                                                        <Input
                                                            type="password"
                                                            className="max-w-md font-mono text-sm"
                                                            placeholder={rerankApiKeySet ? "••••••••" : "co-..."}
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
                                    )}
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
