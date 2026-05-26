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
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select"
import type { TurAIAgent } from "@/models/agent/ai-agent.model"
import type { TurSEInstance } from "@/models/se/se-instance.model"
import type {
    TurRagBm25CoreStatus,
    TurSNSiteGenAi,
} from "@/models/sn/sn-site-genai.model"
import { useUpdateSnSite } from "@/api/queries/sn-site.queries"
import type { TurSNSite } from "@/models/sn/sn-site.model"
import { useSmoothedEta } from "@/hooks/use-smoothed-eta"
import { formatTime } from "@/lib/format-time"
import { TurAIAgentService } from "@/services/agent/ai-agent.service"
import { postLlmChat } from "@viglet/turing-react-sdk"
import { TurSEInstanceService } from "@/services/se/se.service"
import { TurSNSiteService } from "@/services/sn/sn.service"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"
import { IconBrain, IconCpu2, IconDeviceFloppy, IconFileDescription, IconLoader2, IconRefresh, IconSparkles, IconX } from "@tabler/icons-react"
import React, { useCallback, useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { toast } from "@viglet/viglet-design-system"
import { Button } from "../../ui/button"
import { StickyPageHeader } from "../../sticky-page-header"
import { GradientButton } from "../../ui/gradient-button"
import { SectionCard } from "../../ui/section-card"
import { Textarea } from "../../ui/textarea"

const turSNSiteService = new TurSNSiteService();
const turAIAgentService = new TurAIAgentService();
const turSEInstanceService = new TurSEInstanceService();
const turGlobalSettingsService = new TurGlobalSettingsService();

const NONE_VALUE = "__none__";

interface Props {
    snSite: TurSNSite;
}

export const SNSiteGenAiForm: React.FC<Props> = ({ snSite }) => {
    const { t } = useTranslation();
    const updateMutation = useUpdateSnSite();
    const form = useForm<TurSNSiteGenAi>({
        defaultValues: {
            ...snSite.turSNSiteGenAi
        },
    });
    const [aiAgents, setAiAgents] = useState<TurAIAgent[]>([]);
    const [seInstances, setSeInstances] = useState<TurSEInstance[]>([]);
    const [ragCores, setRagCores] = useState<TurRagBm25CoreStatus[]>([]);
    const [isProvisioning, setIsProvisioning] = useState(false);
    const [defaultLlmId, setDefaultLlmId] = useState<string>("");
    const [isGenerating, setIsGenerating] = useState(false);
    const [showOverwriteDialog, setShowOverwriteDialog] = useState(false);
    const [isReindexing, setIsReindexing] = useState(false);
    const [reindexProgress, setReindexProgress] = useState<{
        processed: number;
        total: number;
        phase: string;
        rawEtaMs: number;
        parallelism: number;
    } | null>(null);
    // Smoothed ETA via shared hook — same logic the export/import progress bar uses.
    const reindexEtaMs = useSmoothedEta(
        reindexProgress?.rawEtaMs,
        { tick: reindexProgress?.processed },
    );

    useEffect(() => {
        turAIAgentService.query().then((all) =>
            setAiAgents(all.filter((a) => a.enabled === 1)),
        );
        turSEInstanceService
            .query()
            .then((all) => setSeInstances(all.filter((i) => i.enabled === 1)))
            .catch((err) => console.error("Failed to load SE instances", err));
        turGlobalSettingsService.query().then((settings) => {
            setDefaultLlmId(settings.defaultLlmId ?? "");
        });
    }, []);

    useEffect(() => {
        form.reset({
            ...snSite.turSNSiteGenAi,
            sitePrompt: snSite.turSNSiteGenAi?.sitePrompt ?? "",
            // T19/T24 reposition (2026.2.7) — flags moved here from
            // TurAIAgent. Defaults match the new column defaults so an
            // existing GenAi binding loaded from a pre-T19 backend
            // (undefined) still renders the toggles in the ON state.
            ragBm25Fallback: snSite.turSNSiteGenAi?.ragBm25Fallback ?? true,
            ragHybridSearch: snSite.turSNSiteGenAi?.ragHybridSearch ?? true,
            // T24b — BM25 source defaults to EMBEDDED so a pre-T24b
            // binding renders the radio in the safe "no SE selected" state.
            ragBm25Source: snSite.turSNSiteGenAi?.ragBm25Source ?? "EMBEDDED",
            ragSeInstance: snSite.turSNSiteGenAi?.ragSeInstance ?? null,
        });
    }, [snSite]);

    // T24b — load the per-locale core status table whenever the site id
    // changes, plus on focus (admin may provision in another tab). The
    // status panel only renders when source=SE_INSTANCE, but we cache the
    // payload eagerly so the table doesn't flash empty on radio flip.
    const loadRagCores = useCallback(async () => {
        if (!snSite.id) return;
        const rows = await turSNSiteService.getRagCores(snSite.id);
        setRagCores(rows);
    }, [snSite.id]);

    useEffect(() => {
        loadRagCores();
    }, [loadRagCores]);

    const selectedAgent = form.watch("turAIAgent");
    const ragEnabled = !!selectedAgent && selectedAgent.ragEnabled === true;
    // Reindex is gated on the *persisted* agent, not the form preview: the
    // backend rejects (403) until the chosen agent is saved on this site.
    // We also block while the form is dirty so the user can't click reindex
    // immediately after picking a different agent without saving it first.
    const savedAgent = snSite.turSNSiteGenAi?.turAIAgent;
    const savedRagEnabled =
        !!savedAgent && savedAgent.enabled === 1 && savedAgent.ragEnabled === true;
    const canReindex = savedRagEnabled && !form.formState.isDirty;
    const reindexTitleHint = canReindex
        ? undefined
        : t("forms.snGenai.reindexRequiresSave");

    function handleAgentChange(val: string) {
        if (val === NONE_VALUE) {
            form.setValue("turAIAgent", null);
            return;
        }
        const agent = aiAgents.find((a) => a.id === val);
        form.setValue("turAIAgent", agent ?? null);
    }

    const generateSitePrompt = useCallback(async () => {
        if (!defaultLlmId || !snSite.description) return;

        setIsGenerating(true);
        let generated = "";

        try {
            await postLlmChat(
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
                {
                    onToken: (token) => {
                        generated += token;
                        form.setValue("sitePrompt", generated);
                    },
                },
            );
            toast.success(t("forms.snGenai.promptGenerated"));
        } catch (error) {
            console.error("Failed to generate site prompt", error);
            toast.error(t("forms.snGenai.promptGenerateFailed"));
        } finally {
            setIsGenerating(false);
        }
    }, [defaultLlmId, snSite.name, snSite.description, form, t]);

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

    const subscribeToReindex = useCallback(async (taskId: string, notifyOnComplete: boolean) => {
        await turSNSiteService.subscribeExportProgress(
            taskId,
            (data) => {
                setReindexProgress({
                    processed: data.processedDocuments,
                    total: data.totalDocuments,
                    phase: data.phase,
                    rawEtaMs: data.estimatedRemainingMillis,
                    parallelism: data.parallelism ?? 1,
                });
            },
            () => {
                setIsReindexing(false);
                setReindexProgress(null);
                if (notifyOnComplete) {
                    toast.success(t("forms.snGenai.reindexComplete"));
                }
            },
            (error) => {
                setIsReindexing(false);
                setReindexProgress(null);
                console.error("RAG reindex SSE error", error);
                toast.error(t("forms.snGenai.reindexFailed"));
            },
        );
    }, [t]);

    const handleReindexClick = useCallback(async () => {
        if (isReindexing) return;
        setIsReindexing(true);
        setReindexProgress({ processed: 0, total: 0, phase: "starting", rawEtaMs: 0, parallelism: 1 });

        const taskId = `rag-reindex-${snSite.id}-${Date.now()}`;
        const startedTaskId = await turSNSiteService.reindexGenAi(snSite.id, taskId);
        if (!startedTaskId) {
            setIsReindexing(false);
            setReindexProgress(null);
            toast.error(t("forms.snGenai.reindexFailed"));
            return;
        }
        await subscribeToReindex(startedTaskId, true);
    }, [isReindexing, snSite.id, subscribeToReindex, t]);

    // Auto-resume: when the form remounts (admin navigated away and came back)
    // check whether a reindex is still running on the server for this site and
    // re-attach the progress UI to its SSE stream.
    useEffect(() => {
        let cancelled = false;
        turSNSiteService.getActiveReindex(snSite.id).then((status) => {
            if (cancelled || !status) return;
            setIsReindexing(true);
            setReindexProgress({
                processed: status.processedDocuments,
                total: status.totalDocuments,
                phase: status.phase,
                rawEtaMs: status.estimatedRemainingMillis,
                parallelism: status.parallelism ?? 1,
            });
            subscribeToReindex(status.taskId, false);
        });
        return () => {
            cancelled = true;
        };
    }, [snSite.id, subscribeToReindex]);

    async function onSubmit(data: TurSNSiteGenAi) {
        try {
            const updatedSite = {
                ...snSite,
                turSNSiteGenAi: {
                    ...data,
                    sitePrompt: data.sitePrompt ?? "",
                },
            };
            const result = await updateMutation.mutateAsync(updatedSite);
            if (result) {
                toast.success(t("forms.snGenai.siteUpdated", { name: snSite.name }));
            } else {
                toast.error(t("forms.snGenai.updateFailed"));
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error(t("forms.snGenai.updateFailed"));
        }
    }

    const canGenerate = !!defaultLlmId && !!snSite.description && !isGenerating;

    return (
        <>
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 px-4 lg:px-6 pb-8">
                <StickyPageHeader>
                    <StickyPageHeader.Title
                        icon={IconCpu2}
                        feature={t("sn.genai.title")}
                        description={t("sn.genai.description")}
                    />
                    <StickyPageHeader.Actions>
                        <GradientButton type="submit" size="sm">
                            <IconDeviceFloppy className="size-4" />
                            {t("forms.formActions.saveChanges")}
                        </GradientButton>
                        <GradientButton type="button" variant="outline" size="sm" onClick={() => form.reset(snSite.turSNSiteGenAi)}>
                            <IconX className="size-4" />
                            {t("forms.formActions.cancel")}
                        </GradientButton>
                    </StickyPageHeader.Actions>
                </StickyPageHeader>
                    <div className="space-y-4">
                        {/* Section 1: Site Prompt */}
                        <SectionCard variant="amber">
                            <SectionCard.Header icon={IconFileDescription} title={t("forms.snGenai.sitePrompt")} description={t("forms.snGenai.sitePromptDesc")} />
                            <SectionCard.Content>
                                <FormField
                                    control={form.control}
                                    name="sitePrompt"
                                    render={({ field }) => (
                                        <FormItem>
                                            <FormLabel>{t("forms.snGenai.siteDescriptionPrompt")}</FormLabel>
                                            <FormDescription>
                                                {t("forms.snGenai.siteDescPromptDesc")}
                                            </FormDescription>
                                            <FormControl>
                                                <Textarea
                                                    placeholder={t("forms.snGenai.siteDescPromptPlaceholder")}
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
                                        {isGenerating ? t("forms.snGenai.generating") : t("forms.snGenai.generateWithAi")}
                                    </GradientButton>
                                    <span className="text-xs text-muted-foreground">
                                        {!defaultLlmId
                                            ? t("forms.snGenai.configureLlm")
                                            : !snSite.description
                                                ? t("forms.snGenai.addSiteDescription")
                                                : t("forms.snGenai.generatePromptHint")}
                                    </span>
                                </div>
                            </SectionCard.Content>
                        </SectionCard>

                        {/* Section 2: AI Agent (single source of truth for LLM, embedding, store and RAG flag) */}
                        <SectionCard variant="violet">
                            <SectionCard.Header icon={IconBrain} title={t("forms.snGenai.aiAgent")} description={t("forms.snGenai.aiAgentDesc")} />
                            <SectionCard.Content>
                                <FormField
                                    control={form.control}
                                    name="turAIAgent"
                                    render={({ field }) => (
                                        <FormItemTwoColumns>
                                            <FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Label>{t("forms.snGenai.aiAgent")}</FormItemTwoColumns.Label>
                                                <FormItemTwoColumns.Description>
                                                    {t("forms.snGenai.aiAgentDesc")}
                                                </FormItemTwoColumns.Description>
                                            </FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Right>
                                                <FormControl>
                                                    <Select
                                                        onValueChange={handleAgentChange}
                                                        value={field.value?.id ?? NONE_VALUE}
                                                    >
                                                        <SelectTrigger className="w-full">
                                                            <SelectValue placeholder={t("forms.snGenai.noAiAgent")} />
                                                        </SelectTrigger>
                                                        <SelectContent>
                                                            <SelectItem value={NONE_VALUE}>{t("forms.snGenai.noAiAgent")}</SelectItem>
                                                            {aiAgents.map((agent) => (
                                                                <SelectItem key={agent.id} value={agent.id}>
                                                                    {agent.title}
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
                                {selectedAgent && (
                                    <div className="rounded-lg bg-muted/40 px-4 py-3 mt-2 text-xs text-muted-foreground space-y-1">
                                        <div>
                                            <strong>RAG:</strong>{" "}
                                            {ragEnabled
                                                ? t("forms.snGenai.ragEnabledOnAgent")
                                                : t("forms.snGenai.ragDisabledOnAgent")}
                                        </div>
                                        {selectedAgent.turEmbeddingModelInstance?.modelName && (
                                            <div>
                                                <strong>{t("forms.snGenai.embeddingModel")}:</strong>{" "}
                                                {selectedAgent.turEmbeddingModelInstance.modelName}
                                            </div>
                                        )}
                                        {selectedAgent.turStoreInstance?.title && (
                                            <div>
                                                <strong>{t("forms.snGenai.embeddingStore")}:</strong>{" "}
                                                {selectedAgent.turStoreInstance.title}
                                            </div>
                                        )}
                                        {selectedAgent.llmInstances?.[0]?.title && (
                                            <div>
                                                <strong>{t("forms.snGenai.languageModel")}:</strong>{" "}
                                                {selectedAgent.llmInstances[0].title}
                                            </div>
                                        )}
                                    </div>
                                )}
                                {ragEnabled && (
                                    <div className="flex items-center gap-3 pt-4 border-t border-border/40">
                                        <GradientButton
                                            type="button"
                                            variant="outline"
                                            disabled={isReindexing || !canReindex}
                                            onClick={handleReindexClick}
                                            className="min-w-[320px] justify-center tabular-nums"
                                            title={reindexTitleHint}
                                        >
                                            {isReindexing ? (
                                                <IconLoader2 className="size-4 animate-spin shrink-0" />
                                            ) : (
                                                <IconRefresh className="size-4 shrink-0" />
                                            )}
                                            {isReindexing && reindexProgress
                                                ? t("forms.snGenai.reindexing", {
                                                    processed: reindexProgress.processed.toLocaleString(),
                                                    total: reindexProgress.total.toLocaleString(),
                                                })
                                                : t("forms.snGenai.reindexAll")}
                                        </GradientButton>
                                        <span className="text-xs text-muted-foreground">
                                            {isReindexing
                                                ? reindexEtaMs > 0
                                                    ? t("forms.snGenai.reindexEtaWithThreads", {
                                                        time: formatTime(reindexEtaMs),
                                                        threads: reindexProgress?.parallelism ?? 1,
                                                    })
                                                    : t("forms.snGenai.reindexEtaCalculatingWithThreads", {
                                                        threads: reindexProgress?.parallelism ?? 1,
                                                    })
                                                : t("forms.snGenai.reindexHint")}
                                        </span>
                                    </div>
                                )}
                            </SectionCard.Content>
                        </SectionCard>

                        {/* T19/T24 — Hybrid Retrieval card.
                            Only meaningful when the bound agent has RAG enabled;
                            disabled state when ragEnabled is false makes the
                            UI track the runtime gating exactly. */}
                        <SectionCard variant="blue">
                            <SectionCard.Header
                                icon={IconSparkles}
                                title={t("forms.snGenai.hybridTitle")}
                                description={t("forms.snGenai.hybridDesc")}
                            />
                            <SectionCard.Content>
                                <FormField
                                    control={form.control}
                                    name="ragBm25Fallback"
                                    render={({ field }) => (
                                        <FormItemTwoColumns>
                                            <FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Label>
                                                    {t("forms.snGenai.bm25Fallback")}
                                                </FormItemTwoColumns.Label>
                                                <FormItemTwoColumns.Description>
                                                    {t("forms.snGenai.bm25FallbackDesc")}
                                                </FormItemTwoColumns.Description>
                                            </FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Right>
                                                <FormControl>
                                                    <GradientButton
                                                        type="button"
                                                        variant={field.value !== false ? "default" : "outline"}
                                                        disabled={!ragEnabled}
                                                        onClick={() => field.onChange(field.value === false)}
                                                    >
                                                        {field.value !== false
                                                            ? t("forms.common.enabled")
                                                            : t("forms.common.disabled")}
                                                    </GradientButton>
                                                </FormControl>
                                            </FormItemTwoColumns.Right>
                                        </FormItemTwoColumns>
                                    )}
                                />
                                <FormField
                                    control={form.control}
                                    name="ragHybridSearch"
                                    render={({ field }) => (
                                        <FormItemTwoColumns>
                                            <FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Label>
                                                    {t("forms.snGenai.hybridSearch")}
                                                </FormItemTwoColumns.Label>
                                                <FormItemTwoColumns.Description>
                                                    {t("forms.snGenai.hybridSearchDesc")}
                                                </FormItemTwoColumns.Description>
                                            </FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Right>
                                                <FormControl>
                                                    <GradientButton
                                                        type="button"
                                                        variant={field.value !== false ? "default" : "outline"}
                                                        // Hybrid is only meaningful when BM25 fallback
                                                        // is on; runtime ignores it otherwise, so the
                                                        // UI mirrors that gating.
                                                        disabled={!ragEnabled || form.watch("ragBm25Fallback") === false}
                                                        onClick={() => field.onChange(field.value === false)}
                                                    >
                                                        {field.value !== false
                                                            ? t("forms.common.enabled")
                                                            : t("forms.common.disabled")}
                                                    </GradientButton>
                                                </FormControl>
                                            </FormItemTwoColumns.Right>
                                        </FormItemTwoColumns>
                                    )}
                                />

                                {/* T24b — BM25 source. EMBEDDED (default)
                                    keeps the T24 in-process Lucene path;
                                    SE_INSTANCE routes BM25 to per-locale
                                    cores on a Solr/ES instance. Only
                                    meaningful when both BM25 fallback +
                                    hybrid fusion are on. */}
                                <FormField
                                    control={form.control}
                                    name="ragBm25Source"
                                    render={({ field }) => (
                                        <FormItemTwoColumns>
                                            <FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Label>
                                                    {t("forms.snGenai.bm25Source")}
                                                </FormItemTwoColumns.Label>
                                                <FormItemTwoColumns.Description>
                                                    {t("forms.snGenai.bm25SourceDesc")}
                                                </FormItemTwoColumns.Description>
                                            </FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Right>
                                                <div className="flex gap-2">
                                                    <GradientButton
                                                        type="button"
                                                        variant={
                                                            (field.value ?? "EMBEDDED") === "EMBEDDED"
                                                                ? "default"
                                                                : "outline"
                                                        }
                                                        disabled={
                                                            !ragEnabled
                                                            || form.watch("ragBm25Fallback") === false
                                                            || form.watch("ragHybridSearch") === false
                                                        }
                                                        onClick={() => {
                                                            field.onChange("EMBEDDED");
                                                            form.setValue("ragSeInstance", null);
                                                        }}
                                                    >
                                                        {t("forms.snGenai.bm25SourceEmbedded")}
                                                    </GradientButton>
                                                    <GradientButton
                                                        type="button"
                                                        variant={field.value === "SE_INSTANCE" ? "default" : "outline"}
                                                        disabled={
                                                            !ragEnabled
                                                            || form.watch("ragBm25Fallback") === false
                                                            || form.watch("ragHybridSearch") === false
                                                        }
                                                        onClick={() => field.onChange("SE_INSTANCE")}
                                                    >
                                                        {t("forms.snGenai.bm25SourceSeInstance")}
                                                    </GradientButton>
                                                </div>
                                            </FormItemTwoColumns.Right>
                                        </FormItemTwoColumns>
                                    )}
                                />

                                {form.watch("ragBm25Source") === "SE_INSTANCE" && (
                                    <>
                                        <FormField
                                            control={form.control}
                                            name="ragSeInstance"
                                            render={({ field }) => (
                                                <FormItemTwoColumns>
                                                    <FormItemTwoColumns.Left>
                                                        <FormItemTwoColumns.Label>
                                                            {t("forms.snGenai.seInstance")}
                                                        </FormItemTwoColumns.Label>
                                                        <FormItemTwoColumns.Description>
                                                            {t("forms.snGenai.seInstanceDesc")}
                                                        </FormItemTwoColumns.Description>
                                                    </FormItemTwoColumns.Left>
                                                    <FormItemTwoColumns.Right>
                                                        <Select
                                                            value={field.value?.id ?? NONE_VALUE}
                                                            onValueChange={(val) => {
                                                                if (val === NONE_VALUE) {
                                                                    field.onChange(null);
                                                                    return;
                                                                }
                                                                const inst = seInstances.find((i) => i.id === val);
                                                                field.onChange(inst ?? null);
                                                            }}
                                                            disabled={!ragEnabled}
                                                        >
                                                            <SelectTrigger>
                                                                <SelectValue placeholder={t("forms.snGenai.seInstancePlaceholder")} />
                                                            </SelectTrigger>
                                                            <SelectContent>
                                                                <SelectItem value={NONE_VALUE}>
                                                                    {t("forms.snGenai.seInstanceNone")}
                                                                </SelectItem>
                                                                {seInstances.map((inst) => (
                                                                    <SelectItem key={inst.id} value={inst.id}>
                                                                        {inst.title} ({inst.turSEVendor?.title ?? "?"})
                                                                    </SelectItem>
                                                                ))}
                                                            </SelectContent>
                                                        </Select>
                                                    </FormItemTwoColumns.Right>
                                                </FormItemTwoColumns>
                                            )}
                                        />

                                        {/* Per-locale BM25 core status table.
                                            One row per TurSNSiteLocale; status
                                            color signals lifecycle. Provision
                                            button is gated on a saved binding
                                            (form not dirty) and a non-null SE. */}
                                        <div className="mt-2 space-y-2">
                                            <div className="flex items-center justify-between">
                                                <div>
                                                    <div className="text-sm font-medium">
                                                        {t("forms.snGenai.coresTitle")}
                                                    </div>
                                                    <div className="text-xs text-muted-foreground">
                                                        {t("forms.snGenai.coresDesc")}
                                                    </div>
                                                </div>
                                                <div className="flex gap-2">
                                                    <Button
                                                        type="button"
                                                        variant="outline"
                                                        size="sm"
                                                        onClick={loadRagCores}
                                                        disabled={isProvisioning}
                                                    >
                                                        <IconRefresh className="size-4" />
                                                        {t("forms.snGenai.coresRefresh")}
                                                    </Button>
                                                    <GradientButton
                                                        type="button"
                                                        disabled={
                                                            isProvisioning
                                                            || form.formState.isDirty
                                                            || !snSite.turSNSiteGenAi?.ragSeInstance
                                                        }
                                                        title={
                                                            form.formState.isDirty
                                                                ? t("forms.snGenai.coresSaveFirst")
                                                                : !snSite.turSNSiteGenAi?.ragSeInstance
                                                                    ? t("forms.snGenai.coresPickSe")
                                                                    : undefined
                                                        }
                                                        onClick={async () => {
                                                            setIsProvisioning(true);
                                                            try {
                                                                const rows = await turSNSiteService
                                                                    .provisionRagCores(snSite.id);
                                                                setRagCores(rows);
                                                                toast.success(t("forms.snGenai.coresProvisionDone"));
                                                            } finally {
                                                                setIsProvisioning(false);
                                                            }
                                                        }}
                                                    >
                                                        {isProvisioning && (
                                                            <IconLoader2 className="size-4 animate-spin" />
                                                        )}
                                                        {t("forms.snGenai.coresProvisionAll")}
                                                    </GradientButton>
                                                </div>
                                            </div>
                                            <div className="rounded-md border">
                                                <table className="w-full text-sm">
                                                    <thead className="bg-muted/50 text-left">
                                                        <tr>
                                                            <th className="px-3 py-2 font-medium">
                                                                {t("forms.snGenai.coresColLocale")}
                                                            </th>
                                                            <th className="px-3 py-2 font-medium">
                                                                {t("forms.snGenai.coresColName")}
                                                            </th>
                                                            <th className="px-3 py-2 font-medium">
                                                                {t("forms.snGenai.coresColStatus")}
                                                            </th>
                                                            <th className="px-3 py-2 font-medium text-right">
                                                                {t("forms.snGenai.coresColDocs")}
                                                            </th>
                                                        </tr>
                                                    </thead>
                                                    <tbody>
                                                        {ragCores.length === 0 && (
                                                            <tr>
                                                                <td colSpan={4} className="px-3 py-4 text-center text-xs text-muted-foreground">
                                                                    {t("forms.snGenai.coresEmpty")}
                                                                </td>
                                                            </tr>
                                                        )}
                                                        {ragCores.map((row) => (
                                                            <tr key={row.locale} className="border-t">
                                                                <td className="px-3 py-2 font-mono text-xs">{row.locale}</td>
                                                                <td className="px-3 py-2 font-mono text-xs text-muted-foreground">{row.coreName}</td>
                                                                <td className="px-3 py-2">
                                                                    <span
                                                                        className={
                                                                            row.status === "PROVISIONED"
                                                                                ? "text-emerald-600 dark:text-emerald-400"
                                                                                : row.status === "ERROR"
                                                                                    ? "text-red-600 dark:text-red-400"
                                                                                    : row.status === "PROVISIONING" || row.status === "DELETING"
                                                                                        ? "text-blue-600 dark:text-blue-400"
                                                                                        : "text-muted-foreground"
                                                                        }
                                                                        title={row.lastError ?? undefined}
                                                                    >
                                                                        {row.status}
                                                                    </span>
                                                                </td>
                                                                <td className="px-3 py-2 text-right font-mono text-xs">{row.docCount}</td>
                                                            </tr>
                                                        ))}
                                                    </tbody>
                                                </table>
                                            </div>
                                        </div>
                                    </>
                                )}
                            </SectionCard.Content>
                        </SectionCard>

                    </div>

                </form>
        </Form>

            <Dialog open={showOverwriteDialog} onOpenChange={setShowOverwriteDialog}>
                <DialogContent className="sm:max-w-md">
                    <DialogHeader>
                        <DialogTitle>{t("forms.snGenai.overwriteTitle")}</DialogTitle>
                        <DialogDescription>
                            {t("forms.snGenai.overwriteDesc")}
                        </DialogDescription>
                    </DialogHeader>
                    <DialogFooter className="flex-col gap-2 sm:flex-row">
                        <Button variant="outline" onClick={() => setShowOverwriteDialog(false)}>
                            Cancel
                        </Button>
                        <GradientButton onClick={handleOverwriteConfirm}>
                            <IconSparkles className="size-4" />
                            {t("forms.snGenai.overwriteAndGenerate")}
                        </GradientButton>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </>
    );
};
