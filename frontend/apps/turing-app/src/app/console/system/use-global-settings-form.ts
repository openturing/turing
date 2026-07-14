import type { TurAIAgent } from "@/models/agent/ai-agent.model";
import type { TurEmbeddingModel } from "@/models/embedding/embedding-model.model";
import type { TurLLMInstance } from "@/models/llm/llm-instance.model";
import type { TurGlobalSettings } from "@/models/system/global-settings.model";
import type { TurStoreInstance } from "@/models/store/store-instance.model";
import { TurAIAgentService } from "@/services/agent/ai-agent.service";
import { TurEmbeddingModelService } from "@/services/embedding/embedding-model.service";
import { TurLLMInstanceService } from "@/services/llm/llm.service";
import { TurStoreInstanceService } from "@/services/store/store.service";
import { TurGlobalSettingsService } from "@/services/system/global-settings.service";
import { toast } from "@viglet/viglet-design-system";
import { useEffect, useState } from "react";
import { useForm, type UseFormReturn } from "react-hook-form";
import { useTranslation } from "react-i18next";

const turGlobalSettingsService = new TurGlobalSettingsService();
const turLLMInstanceService = new TurLLMInstanceService();
const turEmbeddingModelService = new TurEmbeddingModelService();
const turStoreInstanceService = new TurStoreInstanceService();
const turAIAgentService = new TurAIAgentService();

const DEFAULT_SETTINGS: TurGlobalSettings = {
    decimalSeparator: "DOT",
    defaultLlmId: "",
    modelLaneFastId: "",
    modelLaneReasoningId: "",
    modelLaneCheapId: "",
    llmFallbackChainIds: [],
    llmFallbackMode: "PRIORITY",
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
    ragSnRerankRegion: "us-east-1",
    ragSnRerankVertexProject: "",
    ragSnRerankVertexLocation: "global",
    ragSnRerankApiKey: "",
    ragSnRerankApiKeySet: false,
    ragSnRerankCacheEnabled: false,
    transcriptionStrategy: "OPENAI",
    transcriptionEndpoint: "",
    transcriptionModel: "",
    transcriptionMaxUploadBytes: 26214400,
    transcriptionApiKey: "",
    transcriptionApiKeySet: false,
    urlFetchMode: "SIMPLE",
    urlFetchBrowserlessUrl: "",
    urlFetchBrowserlessToken: "",
    urlFetchBrowserlessTokenSet: false,
};

export interface BrowserlessStatus {
    available: boolean;
    version: string | null;
    error: string | null;
}

export interface DockerStatus {
    available: boolean;
    serverVersion: string | null;
    error: string | null;
}

/**
 * Shared state for the Global Settings surface. The single `TurGlobalSettings`
 * record is edited across two admin pages — "Settings" (formatting + email) and
 * "Generative AI" (Python, URL-signing secret, PII retention, LLM, RAG, rerank).
 * Both pages call this hook independently; each PUT sends the full object, so a
 * page that renders only a subset of fields still round-trips the rest at their
 * loaded values.
 *
 * @since 2026.3.4
 */
export interface GlobalSettingsFormState {
    form: UseFormReturn<TurGlobalSettings>;
    settings: TurGlobalSettings;
    isLoading: boolean;
    isSaving: boolean;
    onSubmit: (data: TurGlobalSettings) => Promise<void>;
    // Reference data for the select fields.
    llmInstances: TurLLMInstance[];
    embeddingModels: TurEmbeddingModel[];
    storeInstances: TurStoreInstance[];
    aiAgents: TurAIAgent[];
    // Email section.
    isSendingTest: boolean;
    onSendTestEmail: () => Promise<void>;
    // URL-signing secret section (status-only; value never crosses the wire).
    secretConfigured: boolean | null;
    secretPreview: string | null;
    isRotatingSecret: boolean;
    onRotateUrlSigningSecret: () => Promise<void>;
    // Code Interpreter Docker probe.
    dockerStatus: DockerStatus | null;
    isCheckingDocker: boolean;
    onCheckDocker: () => Promise<void>;
    // T739 — browserless sidecar probe (headless URL fetching).
    browserlessStatus: BrowserlessStatus | null;
    isCheckingBrowserless: boolean;
    onCheckBrowserless: () => Promise<void>;
}

export function useGlobalSettingsForm(): GlobalSettingsFormState {
    const { t } = useTranslation();

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
    const [dockerStatus, setDockerStatus] = useState<DockerStatus | null>(null);
    const [isCheckingDocker, setIsCheckingDocker] = useState(false);
    // T739 — browserless reachability probe for headless URL fetching. Only
    // meaningful when the URL-fetch mode is HEADLESS/AUTO; lets the admin
    // confirm the sidecar responds before saving.
    const [browserlessStatus, setBrowserlessStatus] = useState<BrowserlessStatus | null>(null);
    const [isCheckingBrowserless, setIsCheckingBrowserless] = useState(false);
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

    const onCheckBrowserless = async () => {
        try {
            setIsCheckingBrowserless(true);
            const status = await turGlobalSettingsService.getBrowserlessStatus();
            setBrowserlessStatus(status);
            if (status.available) {
                toast.success(t("globalSettings.browserlessCheckSuccess", { version: status.version ?? "" }));
            } else {
                toast.error(t("globalSettings.browserlessCheckFailed", { error: status.error ?? "" }));
            }
        } catch (error) {
            console.error("browserless status check failed", error);
            setBrowserlessStatus({ available: false, version: null, error: "request failed" });
            toast.error(t("globalSettings.browserlessCheckFailed", { error: "" }));
        } finally {
            setIsCheckingBrowserless(false);
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

    return {
        form,
        settings,
        isLoading,
        isSaving,
        onSubmit,
        llmInstances,
        embeddingModels,
        storeInstances,
        aiAgents,
        isSendingTest,
        onSendTestEmail,
        secretConfigured,
        secretPreview,
        isRotatingSecret,
        onRotateUrlSigningSecret,
        dockerStatus,
        isCheckingDocker,
        onCheckDocker,
        browserlessStatus,
        isCheckingBrowserless,
        onCheckBrowserless,
    };
}
