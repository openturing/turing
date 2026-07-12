import { SectionCard } from "@/components/ui/section-card";
import {
    FormControl,
    FormDescription,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from "@/components/ui/form";
import { Input } from "@/components/ui/input";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { Button } from "@/components/ui/button";
import { TurGlobalSettingsService } from "@/services/system/global-settings.service";
import { IconMicrophone } from "@tabler/icons-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { GlobalSettingsFormState } from "../use-global-settings-form";

const service = new TurGlobalSettingsService();

interface FfmpegStatus {
    available: boolean;
    version: string | null;
    error: string | null;
}

/**
 * T687 / §XLII.1 — Transcription (Speech-to-Text) backend. Global (not
 * per-agent): one per-install choice, transversal to persona-from-audio, the
 * AUDIO slot and voice. Modeled on {@link RagRerankSection}: a strategy dropdown
 * whose connection fields appear conditionally. Empty/legacy config keeps
 * today's behavior (OpenAI-compatible, piggybacking the default LLM instance).
 * The same settings are also accepted as `turing.transcription.*` env/props for
 * headless / Viglet Cloud deploys.
 *
 * @since 2026.3.4
 */
export function TranscriptionSection({ state }: { state: GlobalSettingsFormState }) {
    const { t } = useTranslation();
    const { form, isLoading, settings } = state;
    const strategy = form.watch("transcriptionStrategy") ?? "OPENAI";
    const apiKeySet = settings.transcriptionApiKeySet ?? false;
    const [ffmpegStatus, setFfmpegStatus] = useState<FfmpegStatus | null>(null);
    const [checkingFfmpeg, setCheckingFfmpeg] = useState(false);

    const onCheckFfmpeg = async () => {
        try {
            setCheckingFfmpeg(true);
            setFfmpegStatus(await service.getFfmpegStatus());
        } catch {
            setFfmpegStatus({ available: false, version: null, error: "request failed" });
        } finally {
            setCheckingFfmpeg(false);
        }
    };

    return (
        <SectionCard variant="violet">
            <SectionCard.Header
                icon={IconMicrophone}
                title={t("globalSettings.transcriptionTitle")}
                description={t("globalSettings.transcriptionDesc")}
            />
            <SectionCard.Content>
                <FormField
                    control={form.control}
                    name="transcriptionStrategy"
                    render={({ field }) => (
                        <FormItem>
                            <FormLabel>{t("globalSettings.transcriptionStrategy")}</FormLabel>
                            <FormDescription>
                                {t("globalSettings.transcriptionStrategyDesc")}
                            </FormDescription>
                            <FormControl>
                                <Select
                                    onValueChange={field.onChange}
                                    value={field.value ?? "OPENAI"}
                                    disabled={isLoading}
                                >
                                    <SelectTrigger className="w-full max-w-xs">
                                        <SelectValue placeholder={t("globalSettings.choose")} />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="OPENAI">{t("globalSettings.transcriptionStrategyOpenai")}</SelectItem>
                                        <SelectItem value="OPENAI_COMPATIBLE">{t("globalSettings.transcriptionStrategyOpenaiCompatible")}</SelectItem>
                                        <SelectItem value="NONE">{t("globalSettings.transcriptionStrategyNone")}</SelectItem>
                                    </SelectContent>
                                </Select>
                            </FormControl>
                            <FormMessage />
                        </FormItem>
                    )}
                />
                {strategy === "OPENAI_COMPATIBLE" && (
                    <FormField
                        control={form.control}
                        name="transcriptionEndpoint"
                        render={({ field }) => (
                            <FormItem>
                                <FormLabel>{t("globalSettings.transcriptionEndpoint")}</FormLabel>
                                <FormDescription>
                                    {t("globalSettings.transcriptionEndpointDesc")}
                                </FormDescription>
                                <FormControl>
                                    <Input
                                        className="max-w-md font-mono text-sm"
                                        placeholder="http://faster-whisper:8000/v1"
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
                {(strategy === "OPENAI" || strategy === "OPENAI_COMPATIBLE") && (
                    <FormField
                        control={form.control}
                        name="transcriptionModel"
                        render={({ field }) => (
                            <FormItem>
                                <FormLabel>{t("globalSettings.transcriptionModel")}</FormLabel>
                                <FormDescription>
                                    {t("globalSettings.transcriptionModelDesc")}
                                </FormDescription>
                                <FormControl>
                                    <Input
                                        className="max-w-md font-mono text-sm"
                                        placeholder="whisper-1"
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
                {(strategy === "OPENAI" || strategy === "OPENAI_COMPATIBLE") && (
                    <FormField
                        control={form.control}
                        name="transcriptionApiKey"
                        render={({ field }) => (
                            <FormItem>
                                <FormLabel>{t("globalSettings.transcriptionApiKey")}</FormLabel>
                                <FormDescription>
                                    {apiKeySet
                                        ? t("globalSettings.transcriptionApiKeySetDesc")
                                        : t("globalSettings.transcriptionApiKeyDesc")}
                                </FormDescription>
                                <FormControl>
                                    <Input
                                        type="password"
                                        className="max-w-md font-mono text-sm"
                                        placeholder={apiKeySet ? "••••••••" : "sk-..."}
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
                {strategy !== "NONE" && (
                    <FormField
                        control={form.control}
                        name="transcriptionMaxUploadBytes"
                        rules={{ min: { value: 1, message: t("globalSettings.transcriptionMaxUploadBytesInvalid") } }}
                        render={({ field }) => (
                            <FormItem>
                                <FormLabel>{t("globalSettings.transcriptionMaxUploadBytes")}</FormLabel>
                                <FormDescription>
                                    {t("globalSettings.transcriptionMaxUploadBytesDesc")}
                                </FormDescription>
                                <FormControl>
                                    <Input
                                        type="number"
                                        min={1}
                                        step={1}
                                        className="max-w-xs"
                                        disabled={isLoading}
                                        {...field}
                                        value={field.value ?? 26214400}
                                        onChange={e => field.onChange(Number(e.target.value))}
                                    />
                                </FormControl>
                                <FormMessage />
                            </FormItem>
                        )}
                    />
                )}
                {strategy !== "NONE" && (
                    <div className="flex flex-col gap-2">
                        <div className="flex items-center gap-3">
                            <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={onCheckFfmpeg}
                                disabled={checkingFfmpeg}
                            >
                                {checkingFfmpeg
                                    ? t("globalSettings.transcriptionFfmpegChecking")
                                    : t("globalSettings.transcriptionFfmpegCheck")}
                            </Button>
                            {ffmpegStatus && (
                                <span
                                    className={
                                        ffmpegStatus.available
                                            ? "text-sm text-emerald-600 dark:text-emerald-400"
                                            : "text-sm text-amber-600 dark:text-amber-400"
                                    }
                                >
                                    {ffmpegStatus.available
                                        ? t("globalSettings.transcriptionFfmpegAvailable", {
                                              version: ffmpegStatus.version ?? "",
                                          })
                                        : t("globalSettings.transcriptionFfmpegUnavailable", {
                                              error: ffmpegStatus.error ?? "",
                                          })}
                                </span>
                            )}
                        </div>
                        <p className="text-sm text-muted-foreground">
                            {t("globalSettings.transcriptionFfmpegDesc")}
                        </p>
                    </div>
                )}
            </SectionCard.Content>
        </SectionCard>
    );
}
