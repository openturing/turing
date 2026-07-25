import { ROUTES } from "@/app/routes.const"
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
import { usePersonas } from "@/api/queries/persona.queries"
import type { TurSNSite } from "@/models/sn/sn-site.model"
import { useSmoothedEta } from "@/hooks/use-smoothed-eta"
import { formatTime } from "@/lib/format-time"
import { TurAIAgentService } from "@/services/agent/ai-agent.service"
import { TurEmbeddingModelService } from "@/services/embedding/embedding-model.service"
import type { TurEmbeddingModel } from "@/models/embedding/embedding-model.model"
import { postLlmChat } from "@viglet/turing-react-sdk"
import { TurSEInstanceService } from "@/services/se/se.service"
import { TurSNSiteService } from "@/services/sn/sn.service"
import { TurGlobalSettingsService } from "@/services/system/global-settings.service"
import { IconArrowsSort, IconBrain, IconCpu2, IconDatabaseSearch, IconDeviceFloppy, IconFileDescription, IconLoader2, IconMovie, IconRefresh, IconRouteAltLeft, IconSparkles, IconUserSearch, IconX } from "@tabler/icons-react"
import React, { useCallback, useEffect, useState } from "react"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { Link } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { BentoHero, BentoScrollSaveBar } from "@/components/bento"
import { SNFormSection, type SNFormChrome } from "@/components/sn/sn-form-section"
import { Button } from "../../ui/button"
import { StickyPageHeader } from "../../sticky-page-header"
import { GradientButton } from "../../ui/gradient-button"
import { Textarea } from "../../ui/textarea"

const turSNSiteService = new TurSNSiteService();
const turAIAgentService = new TurAIAgentService();
const turSEInstanceService = new TurSEInstanceService();
const turGlobalSettingsService = new TurGlobalSettingsService();
const turEmbeddingModelService = new TurEmbeddingModelService();

const NONE_VALUE = "__none__";

interface Props {
    snSite: TurSNSite;
    /** SN instance base route for the bento back-link. Defaults to console. */
    baseRoute?: string;
    /** Render chrome. `console` = StickyPageHeader + SectionCards; `bento` =
     *  BentoHero + frosted BentoFormSection cards (T576). Defaults to console. */
    chrome?: SNFormChrome;
}

export const SNSiteGenAiForm: React.FC<Props> = ({ snSite, baseRoute = ROUTES.SN_INSTANCE, chrome = "console" }) => {
    const { t } = useTranslation();
    const updateMutation = useUpdateSnSite();
    const form = useForm<TurSNSiteGenAi>({
        defaultValues: {
            ...snSite.turSNSiteGenAi
        },
    });
    // T472 — audience personas (kind AUDIENCE/BOTH) selectable as the
    // index-time content-fit reader proxy.
    const { data: personas } = usePersonas();
    const audiencePersonas = (personas ?? []).filter(
        (p) => p.personaKind === "AUDIENCE" || p.personaKind === "BOTH",
    );
    const [aiAgents, setAiAgents] = useState<TurAIAgent[]>([]);
    const [seInstances, setSeInstances] = useState<TurSEInstance[]>([]);
    // T513 — embedding models selectable as a per-site domain embedder override.
    const [embeddingModels, setEmbeddingModels] = useState<TurEmbeddingModel[]>([]);
    const [ragCores, setRagCores] = useState<TurRagBm25CoreStatus[]>([]);
    const [isProvisioning, setIsProvisioning] = useState(false);
    const [defaultLlmId, setDefaultLlmId] = useState<string>("");
    // T622 — global Default AI Agent id, so this form can explain that a site
    // with no agent of its own still runs on that default (fail-open fallback).
    const [defaultAiAgentId, setDefaultAiAgentId] = useState<string>("");
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
            setDefaultAiAgentId(settings.defaultAiAgentId ?? "");
        });
        turEmbeddingModelService
            .query()
            .then((all) => setEmbeddingModels(all.filter((m) => m.enabled === 1)))
            .catch((err) => console.error("Failed to load embedding models", err));
    }, []);

    useEffect(() => {
        form.reset({
            ...snSite.turSNSiteGenAi,
            sitePrompt: snSite.turSNSiteGenAi?.sitePrompt ?? "",
            // T19/T24 reposition (2026.2.7) — flags moved here from
            // TurAIAgent. Defaults match the new column defaults so an
            // existing GenAi binding loaded from a pre-T19 backend
            // (undefined) still renders the toggles in the ON state.
            // T790 / §LIV.1 (Block BF) — knowledge-base mode. VECTOR (default)
            // keeps the classic embedding RAG chat so a pre-T790 binding renders
            // unchanged; VECTORLESS_STRUCTURED is the copilot (no embeddings).
            knowledgeBaseMode: snSite.turSNSiteGenAi?.knowledgeBaseMode ?? "VECTOR",
            // T818/T819 (Block BK) — copilot query-planning strategy + analysis depth.
            // null means "inherit the deployment default" (DETERMINISTIC = today's
            // behaviour), so a pre-BK binding renders the "Default" pill selected and
            // saving the form does not pin anything.
            copilotPlanningStrategy: snSite.turSNSiteGenAi?.copilotPlanningStrategy ?? null,
            copilotPlanningMaxPasses: snSite.turSNSiteGenAi?.copilotPlanningMaxPasses ?? null,
            ragBm25Fallback: snSite.turSNSiteGenAi?.ragBm25Fallback ?? true,
            ragHybridSearch: snSite.turSNSiteGenAi?.ragHybridSearch ?? true,
            // T24b — BM25 source defaults to EMBEDDED so a pre-T24b
            // binding renders the radio in the safe "no SE selected" state.
            ragBm25Source: snSite.turSNSiteGenAi?.ragBm25Source ?? "EMBEDDED",
            ragSeInstance: snSite.turSNSiteGenAi?.ragSeInstance ?? null,
            // T383 — public SN search ranking mode; LEGACY keeps the historical
            // lexical-only ranking so a pre-T383 binding renders the legacy state.
            snRankingMode: snSite.turSNSiteGenAi?.snRankingMode ?? "LEGACY",
            // T513 — per-site domain embedding model override; null = platform default.
            embeddingModelId: snSite.turSNSiteGenAi?.embeddingModelId ?? null,
            // T472 — index-time content-fit signal; off by default so a pre-T472
            // binding renders the disabled state and the indexing path is unchanged.
            contentFitIndexingEnabled: snSite.turSNSiteGenAi?.contentFitIndexingEnabled ?? false,
            contentFitPersonaId: snSite.turSNSiteGenAi?.contentFitPersonaId ?? null,
            // T501 — index-time Gemini video/audio understanding; off by default so
            // a pre-T501 binding renders disabled and the indexing path is unchanged.
            mediaUnderstandingIndexingEnabled:
                snSite.turSNSiteGenAi?.mediaUnderstandingIndexingEnabled ?? false,
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
    // T790 — single source of truth for "vector RAG is active on this site". A
    // VECTORLESS_STRUCTURED site never embeds, so every vector-RAG control (reindex,
    // hybrid-retrieval tuning, ANN) is off regardless of the agent's RAG flag. Fold
    // the mode in here — mirrors the backend TurGenAiContextFactory /
    // TurDefaultAgentResolver.isRagReady gate — so consumers don't each re-check it.
    const vectorModeActive = form.watch("knowledgeBaseMode") !== "VECTORLESS_STRUCTURED";
    // T818/T819 (Block BK) — only the LLM planning strategies spend judge/refine
    // passes, so the analysis-depth control and the cost callout key off this.
    // "Default" (null) is excluded on purpose: its effective strategy lives in
    // configuration, so this form cannot know whether passes will be spent.
    const copilotPlanningStrategy = form.watch("copilotPlanningStrategy");
    const copilotPlanningUsesLlm =
        copilotPlanningStrategy === "LLM_ASSISTED" || copilotPlanningStrategy === "HYBRID";
    const ragEnabled = !!selectedAgent && selectedAgent.ragEnabled === true && vectorModeActive;
    // T622 — when the site picks no agent of its own, chat/RAG/ANN fall back to
    // the global Default AI Agent. Surface which agent that is so the admin sees
    // the site is not actually "off".
    const defaultAgent = defaultAiAgentId
        ? aiAgents.find((a) => a.id === defaultAiAgentId)
        : undefined;
    const usingDefaultAgent = !selectedAgent && !!defaultAgent;
    const defaultAgentRagEnabled = defaultAgent?.ragEnabled === true;
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

    const isBento = chrome === "bento";

    const actions = (
        <>
            <GradientButton type="submit" size="sm">
                <IconDeviceFloppy className="size-4" />
                {t("forms.formActions.saveChanges")}
            </GradientButton>
            <GradientButton type="button" variant="outline" size="sm" onClick={() => form.reset(snSite.turSNSiteGenAi)}>
                <IconX className="size-4" />
                {t("forms.formActions.cancel")}
            </GradientButton>
        </>
    );

    return (
        <>
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className={isBento ? "space-y-5 pb-8" : "space-y-4 px-4 lg:px-6 pb-8"}>
                {isBento ? (
                    <>
                    <BentoHero
                        eyebrow={<Link to={`${baseRoute}/${snSite.id}`} className="hover:text-foreground">{t("sn.title")}</Link>}
                        leading={
                            <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
                                <IconCpu2 size={24} />
                            </span>
                        }
                        title={t("sn.genai.title")}
                        subtitle={t("sn.genai.description")}
                        trailing={<div className="bento-fade-out flex shrink-0 items-center gap-2">{actions}</div>}
                    />
                    <BentoScrollSaveBar onCancel={() => form.reset(snSite.turSNSiteGenAi)} />
                    </>
                ) : (
                    <StickyPageHeader>
                        <StickyPageHeader.Title
                            icon={IconCpu2}
                            feature={t("sn.genai.title")}
                            description={t("sn.genai.description")}
                        />
                        <StickyPageHeader.Actions>{actions}</StickyPageHeader.Actions>
                    </StickyPageHeader>
                )}
                    <div className="space-y-4">
                        {/* Section 1: Site Prompt */}
                        <SNFormSection chrome={chrome} icon={IconFileDescription} tone="amber" title={t("forms.snGenai.sitePrompt")} description={t("forms.snGenai.sitePromptDesc")}>
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
                        </SNFormSection>

                        {/* Section 2: AI Agent (single source of truth for LLM, embedding, store and RAG flag) */}
                        <SNFormSection chrome={chrome} icon={IconBrain} tone="violet" title={t("forms.snGenai.aiAgent")} description={t("forms.snGenai.aiAgentDesc")}>
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
                                {usingDefaultAgent && (
                                    <div className="rounded-lg border border-violet-500/30 bg-violet-500/5 px-4 py-3 mt-2 text-xs text-muted-foreground space-y-1">
                                        <div className="text-foreground">
                                            <strong>{t("forms.snGenai.usingDefaultAgent", { agent: defaultAgent?.title })}</strong>
                                        </div>
                                        <div>{t("forms.snGenai.usingDefaultAgentDesc")}</div>
                                        <div>
                                            <strong>RAG:</strong>{" "}
                                            {defaultAgentRagEnabled
                                                ? t("forms.snGenai.ragEnabledOnAgent")
                                                : t("forms.snGenai.ragDisabledOnAgent")}
                                        </div>
                                    </div>
                                )}
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
                                {/* ragEnabled already folds in the knowledge-base mode
                                    (T790), so a VECTORLESS_STRUCTURED site hides the
                                    reindex button — the backend reindex early-returns anyway. */}
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
                        </SNFormSection>

                        {/* T790 / §LIV.1 (Block BF) — Knowledge base mode. The
                            explicitly-named retrieval strategy for this site's
                            GenAI answers: VECTOR (classic embedding RAG, needs
                            embedding model + store), VECTORLESS_STRUCTURED (the
                            catalog copilot — NL→DSL over the declared field
                            schema, needs only a default LLM), or HYBRID (both).
                            Naming it makes the vectorless path discoverable. */}
                        <SNFormSection chrome={chrome} icon={IconDatabaseSearch} tone="violet" title={t("forms.snGenai.kbModeTitle")} description={t("forms.snGenai.kbModeDesc")}>
                                <FormField
                                    control={form.control}
                                    name="knowledgeBaseMode"
                                    render={({ field }) => (
                                        <FormItemTwoColumns>
                                            <FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Label>
                                                    {t("forms.snGenai.kbMode")}
                                                </FormItemTwoColumns.Label>
                                                <FormItemTwoColumns.Description>
                                                    {(field.value ?? "VECTOR") === "VECTOR"
                                                        ? t("forms.snGenai.kbModeVectorHint")
                                                        : (field.value === "VECTORLESS_STRUCTURED"
                                                            ? t("forms.snGenai.kbModeVectorlessHint")
                                                            : t("forms.snGenai.kbModeHybridHint"))}
                                                </FormItemTwoColumns.Description>
                                            </FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Right>
                                                <div className="flex flex-wrap gap-2">
                                                    <GradientButton
                                                        type="button"
                                                        variant={(field.value ?? "VECTOR") === "VECTOR" ? "default" : "outline"}
                                                        onClick={() => field.onChange("VECTOR")}
                                                    >
                                                        {t("forms.snGenai.kbModeVector")}
                                                    </GradientButton>
                                                    <GradientButton
                                                        type="button"
                                                        variant={field.value === "VECTORLESS_STRUCTURED" ? "default" : "outline"}
                                                        onClick={() => field.onChange("VECTORLESS_STRUCTURED")}
                                                    >
                                                        {t("forms.snGenai.kbModeVectorless")}
                                                    </GradientButton>
                                                    <GradientButton
                                                        type="button"
                                                        variant={field.value === "HYBRID" ? "default" : "outline"}
                                                        onClick={() => field.onChange("HYBRID")}
                                                    >
                                                        {t("forms.snGenai.kbModeHybrid")}
                                                    </GradientButton>
                                                </div>
                                            </FormItemTwoColumns.Right>
                                        </FormItemTwoColumns>
                                    )}
                                />
                                {/* T790 — make the implicit consequence explicit: in
                                    VECTORLESS_STRUCTURED the site never embeds, even when
                                    the (own or default) agent has RAG on. Backend gate:
                                    TurGenAiContextFactory + TurDefaultAgentResolver.isRagReady
                                    + reindexVectorStore early-return. */}
                                {form.watch("knowledgeBaseMode") === "VECTORLESS_STRUCTURED" && (
                                    <div className="rounded-lg border border-amber-500/40 bg-amber-500/5 px-4 py-3 mt-2 text-xs text-muted-foreground space-y-1">
                                        <div className="flex items-center gap-2 text-foreground">
                                            <IconDatabaseSearch className="size-4 text-amber-600 dark:text-amber-400 shrink-0" />
                                            <strong>{t("forms.snGenai.kbModeVectorlessCalloutTitle")}</strong>
                                        </div>
                                        <div>{t("forms.snGenai.kbModeVectorlessCalloutDesc")}</div>
                                    </div>
                                )}
                        </SNFormSection>

                        {/* T818–T821 / §LIX (Block BK) — Copilot Query Planning.
                            Picks HOW the copilot turns a question into a structured
                            query: DETERMINISTIC (today — deterministic ranking overlay
                            + one LLM facet parse), LLM_ASSISTED (multi-pass
                            parse→judge→refine, i18n-native, N× LLM calls) or HYBRID
                            (fast-path, escalating to the LLM only on an empty /
                            degenerate result). "Default" pins nothing and inherits
                            turing.genai.copilot.planning.*. Only meaningful once the
                            copilot is reachable — i.e. a vectorless/hybrid KB mode. */}
                        <SNFormSection chrome={chrome} icon={IconRouteAltLeft} tone="violet" title={t("forms.snGenai.copilotPlanningTitle")} description={t("forms.snGenai.copilotPlanningDesc")}>
                                <FormField
                                    control={form.control}
                                    name="copilotPlanningStrategy"
                                    render={({ field }) => (
                                        <FormItemTwoColumns>
                                            <FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Label>
                                                    {t("forms.snGenai.copilotPlanningStrategy")}
                                                </FormItemTwoColumns.Label>
                                                <FormItemTwoColumns.Description>
                                                    {field.value === "LLM_ASSISTED"
                                                        ? t("forms.snGenai.copilotPlanningLlmHint")
                                                        : field.value === "HYBRID"
                                                            ? t("forms.snGenai.copilotPlanningHybridHint")
                                                            : field.value === "DETERMINISTIC"
                                                                ? t("forms.snGenai.copilotPlanningDeterministicHint")
                                                                : t("forms.snGenai.copilotPlanningDefaultHint")}
                                                </FormItemTwoColumns.Description>
                                            </FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Right>
                                                <div className="flex flex-wrap gap-2">
                                                    <GradientButton
                                                        type="button"
                                                        variant={!field.value ? "default" : "outline"}
                                                        onClick={() => field.onChange(null)}
                                                    >
                                                        {t("forms.snGenai.copilotPlanningDefault")}
                                                    </GradientButton>
                                                    <GradientButton
                                                        type="button"
                                                        variant={field.value === "DETERMINISTIC" ? "default" : "outline"}
                                                        onClick={() => field.onChange("DETERMINISTIC")}
                                                    >
                                                        {t("forms.snGenai.copilotPlanningDeterministic")}
                                                    </GradientButton>
                                                    <GradientButton
                                                        type="button"
                                                        variant={field.value === "LLM_ASSISTED" ? "default" : "outline"}
                                                        onClick={() => field.onChange("LLM_ASSISTED")}
                                                    >
                                                        {t("forms.snGenai.copilotPlanningLlm")}
                                                    </GradientButton>
                                                    <GradientButton
                                                        type="button"
                                                        variant={field.value === "HYBRID" ? "default" : "outline"}
                                                        onClick={() => field.onChange("HYBRID")}
                                                    >
                                                        {t("forms.snGenai.copilotPlanningHybrid")}
                                                    </GradientButton>
                                                </div>
                                            </FormItemTwoColumns.Right>
                                        </FormItemTwoColumns>
                                    )}
                                />

                                {/* T819 — analysis depth. Only the LLM strategies spend
                                    passes, so the control is disabled on DETERMINISTIC
                                    (and on "Default", whose effective strategy lives in
                                    configuration, not here). */}
                                <FormField
                                    control={form.control}
                                    name="copilotPlanningMaxPasses"
                                    render={({ field }) => (
                                        <FormItemTwoColumns>
                                            <FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Label>
                                                    {t("forms.snGenai.copilotPlanningDepth")}
                                                </FormItemTwoColumns.Label>
                                                <FormItemTwoColumns.Description>
                                                    {t("forms.snGenai.copilotPlanningDepthDesc")}
                                                </FormItemTwoColumns.Description>
                                            </FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Right>
                                                <Select
                                                    value={
                                                        field.value === null || field.value === undefined
                                                            ? NONE_VALUE
                                                            : String(field.value)
                                                    }
                                                    onValueChange={(val) =>
                                                        field.onChange(val === NONE_VALUE ? null : Number(val))
                                                    }
                                                    disabled={!copilotPlanningUsesLlm}
                                                >
                                                    <SelectTrigger className="w-full">
                                                        <SelectValue placeholder={t("forms.snGenai.copilotPlanningDepthInherit")} />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value={NONE_VALUE}>
                                                            {t("forms.snGenai.copilotPlanningDepthInherit")}
                                                        </SelectItem>
                                                        <SelectItem value="0">
                                                            {t("forms.snGenai.copilotPlanningDepth0")}
                                                        </SelectItem>
                                                        <SelectItem value="1">
                                                            {t("forms.snGenai.copilotPlanningDepth1")}
                                                        </SelectItem>
                                                        <SelectItem value="2">
                                                            {t("forms.snGenai.copilotPlanningDepth2")}
                                                        </SelectItem>
                                                    </SelectContent>
                                                </Select>
                                            </FormItemTwoColumns.Right>
                                        </FormItemTwoColumns>
                                    )}
                                />

                                {/* Cost callout — an LLM strategy multiplies the per-turn
                                    LLM calls, which is the whole trade-off of the block. */}
                                {copilotPlanningUsesLlm && (
                                    <div className="rounded-lg border border-amber-500/40 bg-amber-500/5 px-4 py-3 mt-2 text-xs text-muted-foreground space-y-1">
                                        <div className="flex items-center gap-2 text-foreground">
                                            <IconRouteAltLeft className="size-4 text-amber-600 dark:text-amber-400 shrink-0" />
                                            <strong>{t("forms.snGenai.copilotPlanningCostTitle")}</strong>
                                        </div>
                                        <div>{t("forms.snGenai.copilotPlanningCostDesc")}</div>
                                    </div>
                                )}
                        </SNFormSection>

                        {/* T19/T24 — Hybrid Retrieval card.
                            Only meaningful when the bound agent has RAG enabled;
                            disabled state when ragEnabled is false makes the
                            UI track the runtime gating exactly. */}
                        <SNFormSection chrome={chrome} icon={IconSparkles} tone="blue" title={t("forms.snGenai.hybridTitle")} description={t("forms.snGenai.hybridDesc")}>
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
                        </SNFormSection>

                        {/* T383 / §XX.3 — ranking mode for the PUBLIC SN search
                            (faceted catalog results). Independent of the RAG
                            card above: this reorders the public results, not
                            the chat knowledge-base retrieval. HYBRID_RRF needs a
                            default embedding model + store in Global Settings;
                            documents are embedded into a per-site collection as
                            they are indexed, and fused with BM25 via RRF when the
                            sort is relevance. */}
                        <SNFormSection chrome={chrome} icon={IconArrowsSort} tone="blue" title={t("forms.snGenai.snRankingTitle")} description={t("forms.snGenai.snRankingDesc")}>
                                <FormField
                                    control={form.control}
                                    name="snRankingMode"
                                    render={({ field }) => (
                                        <FormItemTwoColumns>
                                            <FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Label>
                                                    {t("forms.snGenai.snRankingMode")}
                                                </FormItemTwoColumns.Label>
                                                <FormItemTwoColumns.Description>
                                                    {t("forms.snGenai.snRankingModeDesc")}
                                                </FormItemTwoColumns.Description>
                                            </FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Right>
                                                <div className="flex gap-2">
                                                    <GradientButton
                                                        type="button"
                                                        variant={
                                                            (field.value ?? "LEGACY") === "LEGACY"
                                                                ? "default"
                                                                : "outline"
                                                        }
                                                        onClick={() => field.onChange("LEGACY")}
                                                    >
                                                        {t("forms.snGenai.snRankingLegacy")}
                                                    </GradientButton>
                                                    <GradientButton
                                                        type="button"
                                                        variant={field.value === "HYBRID_RRF" ? "default" : "outline"}
                                                        onClick={() => field.onChange("HYBRID_RRF")}
                                                    >
                                                        {t("forms.snGenai.snRankingHybrid")}
                                                    </GradientButton>
                                                    {/* T389 / §XX.9 — full retrieve → fuse → rerank pipeline:
                                                        HYBRID_RRF plus the Block N reranker strategy (selected
                                                        in Global Settings). */}
                                                    <GradientButton
                                                        type="button"
                                                        variant={field.value === "HYBRID_RRF_RERANK" ? "default" : "outline"}
                                                        onClick={() => field.onChange("HYBRID_RRF_RERANK")}
                                                    >
                                                        {t("forms.snGenai.snRankingRerank")}
                                                    </GradientButton>
                                                </div>
                                            </FormItemTwoColumns.Right>
                                        </FormItemTwoColumns>
                                    )}
                                />

                                {/* T513 / §XXVIII.9 — per-site domain-specialized
                                    embedding model. When set, this site indexes AND
                                    queries its vector collection through the chosen
                                    embedder (e.g. voyage-law / voyage-code / voyage-finance);
                                    blank falls back to the Global Settings default.
                                    Only meaningful when a hybrid ranking mode is on. */}
                                <FormField
                                    control={form.control}
                                    name="embeddingModelId"
                                    render={({ field }) => (
                                        <FormItemTwoColumns>
                                            <FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Label>
                                                    {t("forms.snGenai.domainEmbeddingModel")}
                                                </FormItemTwoColumns.Label>
                                                <FormItemTwoColumns.Description>
                                                    {t("forms.snGenai.domainEmbeddingModelDesc")}
                                                </FormItemTwoColumns.Description>
                                            </FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Right>
                                                <Select
                                                    value={field.value ?? NONE_VALUE}
                                                    onValueChange={(val) =>
                                                        field.onChange(val === NONE_VALUE ? null : val)
                                                    }
                                                    disabled={(form.watch("snRankingMode") ?? "LEGACY") === "LEGACY"}
                                                >
                                                    <SelectTrigger className="w-full">
                                                        <SelectValue placeholder={t("forms.snGenai.domainEmbeddingModelNone")} />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value={NONE_VALUE}>
                                                            {t("forms.snGenai.domainEmbeddingModelNone")}
                                                        </SelectItem>
                                                        {embeddingModels.map((model) => (
                                                            <SelectItem key={model.id} value={model.id}>
                                                                {model.modelName}
                                                                {model.modelReference ? ` (${model.modelReference})` : ""}
                                                            </SelectItem>
                                                        ))}
                                                    </SelectContent>
                                                </Select>
                                            </FormItemTwoColumns.Right>
                                        </FormItemTwoColumns>
                                    )}
                                />
                        </SNFormSection>

                        {/* T472 / §XXVI.9 — index-time audience content-fit signal.
                            Scores each document's readability against an AUDIENCE
                            persona as it is indexed and stores content_fit_score;
                            surfaced in the Field Coverage dashboard. Independent of
                            the RAG / ranking cards above. */}
                        <SNFormSection chrome={chrome} icon={IconUserSearch} tone="emerald" title={t("forms.snGenai.contentFitTitle")} description={t("forms.snGenai.contentFitDesc")}>
                                <FormField
                                    control={form.control}
                                    name="contentFitIndexingEnabled"
                                    render={({ field }) => (
                                        <FormItemTwoColumns>
                                            <FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Label>
                                                    {t("forms.snGenai.contentFitEnable")}
                                                </FormItemTwoColumns.Label>
                                                <FormItemTwoColumns.Description>
                                                    {t("forms.snGenai.contentFitEnableDesc")}
                                                </FormItemTwoColumns.Description>
                                            </FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Right>
                                                <FormControl>
                                                    <GradientButton
                                                        type="button"
                                                        variant={field.value === true ? "default" : "outline"}
                                                        onClick={() => field.onChange(field.value !== true)}
                                                    >
                                                        {field.value === true
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
                                    name="contentFitPersonaId"
                                    render={({ field }) => (
                                        <FormItemTwoColumns>
                                            <FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Label>
                                                    {t("forms.snGenai.contentFitPersona")}
                                                </FormItemTwoColumns.Label>
                                                <FormItemTwoColumns.Description>
                                                    {t("forms.snGenai.contentFitPersonaDesc")}
                                                </FormItemTwoColumns.Description>
                                            </FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Right>
                                                <Select
                                                    value={field.value ?? NONE_VALUE}
                                                    onValueChange={(val) =>
                                                        field.onChange(val === NONE_VALUE ? null : val)
                                                    }
                                                    disabled={
                                                        form.watch("contentFitIndexingEnabled") !== true
                                                        || audiencePersonas.length === 0
                                                    }
                                                >
                                                    <SelectTrigger className="w-full">
                                                        <SelectValue placeholder={t("forms.snGenai.contentFitPersonaNone")} />
                                                    </SelectTrigger>
                                                    <SelectContent>
                                                        <SelectItem value={NONE_VALUE}>
                                                            {t("forms.snGenai.contentFitPersonaNone")}
                                                        </SelectItem>
                                                        {audiencePersonas.map((persona) => (
                                                            <SelectItem key={persona.id} value={persona.id}>
                                                                {persona.name}
                                                            </SelectItem>
                                                        ))}
                                                    </SelectContent>
                                                </Select>
                                                {audiencePersonas.length === 0 && (
                                                    <p className="mt-1 text-xs text-muted-foreground">
                                                        {t("forms.snGenai.contentFitNoPersonas")}
                                                    </p>
                                                )}
                                            </FormItemTwoColumns.Right>
                                        </FormItemTwoColumns>
                                    )}
                                />
                        </SNFormSection>

                        {/* T501 / §X.19 — index-time native video/audio understanding.
                            When a site's GenAI agent is a Gemini instance, a media
                            document's clip is transcribed + scene-described as it is
                            indexed and appended to the text field, so the clip's
                            content is searchable. Gemini-only, fail-open, off by default. */}
                        <SNFormSection chrome={chrome} icon={IconMovie} tone="emerald" title={t("forms.snGenai.mediaUnderstandingTitle")} description={t("forms.snGenai.mediaUnderstandingDesc")}>
                                <FormField
                                    control={form.control}
                                    name="mediaUnderstandingIndexingEnabled"
                                    render={({ field }) => (
                                        <FormItemTwoColumns>
                                            <FormItemTwoColumns.Left>
                                                <FormItemTwoColumns.Label>
                                                    {t("forms.snGenai.mediaUnderstandingEnable")}
                                                </FormItemTwoColumns.Label>
                                                <FormItemTwoColumns.Description>
                                                    {t("forms.snGenai.mediaUnderstandingEnableDesc")}
                                                </FormItemTwoColumns.Description>
                                            </FormItemTwoColumns.Left>
                                            <FormItemTwoColumns.Right>
                                                <FormControl>
                                                    <GradientButton
                                                        type="button"
                                                        variant={field.value === true ? "default" : "outline"}
                                                        onClick={() => field.onChange(field.value !== true)}
                                                    >
                                                        {field.value === true
                                                            ? t("forms.common.enabled")
                                                            : t("forms.common.disabled")}
                                                    </GradientButton>
                                                </FormControl>
                                            </FormItemTwoColumns.Right>
                                        </FormItemTwoColumns>
                                    )}
                                />
                        </SNFormSection>

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
