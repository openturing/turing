import { SectionCard } from "@/components/ui/section-card";
import {
    FormControl,
    FormDescription,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from "@/components/ui/form";
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns";
import { GradientSwitch } from "@/components/ui/gradient-switch";
import { DurationInput } from "@/components/ui/duration-input";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { IconBrain } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type { GlobalSettingsFormState } from "../use-global-settings-form";

/**
 * Default LLM, per-stage model lanes (T517), cross-provider fallback chain
 * (T518), default AI Agent and LLM response cache. Part of the "Generative AI"
 * admin page.
 *
 * @since 2026.3.4
 */
export function LlmSettingsSection({ state }: { state: GlobalSettingsFormState }) {
    const { t } = useTranslation();
    const { form, isLoading, llmInstances, aiAgents } = state;
    const llmCacheEnabled = form.watch("llmCacheEnabled") ?? false;

    return (
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
                {/* T517 — cross-provider per-stage model lanes */}
                {(["modelLaneFastId", "modelLaneReasoningId", "modelLaneCheapId"] as const).map((laneName) => (
                    <FormField
                        key={laneName}
                        control={form.control}
                        name={laneName}
                        render={({ field }) => (
                            <FormItemTwoColumns>
                                <FormItemTwoColumns.Left>
                                    <FormItemTwoColumns.Label>{t(`globalSettings.${laneName}`)}</FormItemTwoColumns.Label>
                                    <FormItemTwoColumns.Description>
                                        {t(`globalSettings.${laneName}Desc`)}
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
                                                <SelectValue placeholder={t("globalSettings.modelLaneDefault")} />
                                            </SelectTrigger>
                                            <SelectContent>
                                                <SelectItem value="none">{t("globalSettings.modelLaneDefault")}</SelectItem>
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
                ))}
                {/* T518 — cost-aware cross-provider fallback chain + routing mode */}
                <FormField
                    control={form.control}
                    name="llmFallbackMode"
                    render={({ field }) => (
                        <FormItemTwoColumns>
                            <FormItemTwoColumns.Left>
                                <FormItemTwoColumns.Label>{t("globalSettings.llmFallbackMode")}</FormItemTwoColumns.Label>
                                <FormItemTwoColumns.Description>
                                    {t("globalSettings.llmFallbackModeDesc")}
                                </FormItemTwoColumns.Description>
                            </FormItemTwoColumns.Left>
                            <FormItemTwoColumns.Right>
                                <FormControl>
                                    <Select
                                        onValueChange={(v) => field.onChange(v)}
                                        value={field.value || "PRIORITY"}
                                        disabled={isLoading}
                                    >
                                        <SelectTrigger className="w-full max-w-xs">
                                            <SelectValue />
                                        </SelectTrigger>
                                        <SelectContent>
                                            <SelectItem value="PRIORITY">{t("globalSettings.llmFallbackModePriority")}</SelectItem>
                                            <SelectItem value="CHEAPEST">{t("globalSettings.llmFallbackModeCheapest")}</SelectItem>
                                        </SelectContent>
                                    </Select>
                                </FormControl>
                            </FormItemTwoColumns.Right>
                        </FormItemTwoColumns>
                    )}
                />
                <FormField
                    control={form.control}
                    name="llmFallbackChainIds"
                    render={({ field }) => {
                        const chain: string[] = field.value ?? [];
                        const toggle = (id: string) =>
                            field.onChange(
                                chain.includes(id)
                                    ? chain.filter((x) => x !== id)
                                    : [...chain, id],
                            );
                        return (
                            <FormItemTwoColumns>
                                <FormItemTwoColumns.Left>
                                    <FormItemTwoColumns.Label>{t("globalSettings.llmFallbackChain")}</FormItemTwoColumns.Label>
                                    <FormItemTwoColumns.Description>
                                        {t("globalSettings.llmFallbackChainDesc")}
                                    </FormItemTwoColumns.Description>
                                </FormItemTwoColumns.Left>
                                <FormItemTwoColumns.Right>
                                    <div className="flex flex-col gap-2">
                                        {llmInstances.length === 0 ? (
                                            <span className="text-sm text-muted-foreground">{t("globalSettings.none")}</span>
                                        ) : (
                                            llmInstances.map((instance) => (
                                                <label key={instance.id} className="flex items-center gap-2 text-sm">
                                                    <GradientSwitch
                                                        checked={chain.includes(instance.id)}
                                                        onCheckedChange={() => toggle(instance.id)}
                                                        disabled={isLoading}
                                                    />
                                                    <span>{instance.title}</span>
                                                </label>
                                            ))
                                        )}
                                    </div>
                                </FormItemTwoColumns.Right>
                            </FormItemTwoColumns>
                        );
                    }}
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
    );
}
