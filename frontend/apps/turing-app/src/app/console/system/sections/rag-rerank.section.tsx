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
import { Input } from "@/components/ui/input";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { IconArrowsSort } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type { GlobalSettingsFormState } from "../use-global-settings-form";

/**
 * T337–T339 — SN RAG reranker. Opt-in (default off); the backend defaults to
 * the legacy LLM strategy. CROSS_ENCODER, COHERE, VOYAGE, BEDROCK and VERTEX_AI
 * reveal their own connection fields. Part of the "Generative AI" admin page.
 *
 * @since 2026.3.4
 */
export function RagRerankSection({ state }: { state: GlobalSettingsFormState }) {
    const { t } = useTranslation();
    const { form, isLoading, settings } = state;
    const rerankEnabled = form.watch("ragSnRerankEnabled") ?? false;
    const rerankStrategy = form.watch("ragSnRerankStrategy") ?? "LLM";
    const rerankApiKeySet = settings.ragSnRerankApiKeySet ?? false;

    return (
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
                                                    <SelectItem value="LLM_LOGPROBS">{t("globalSettings.rerankStrategyLlmLogprobs")}</SelectItem>
                                                    <SelectItem value="CROSS_ENCODER">{t("globalSettings.rerankStrategyCrossEncoder")}</SelectItem>
                                                    <SelectItem value="COHERE">{t("globalSettings.rerankStrategyCohere")}</SelectItem>
                                                    <SelectItem value="VOYAGE">{t("globalSettings.rerankStrategyVoyage")}</SelectItem>
                                                    <SelectItem value="BEDROCK">{t("globalSettings.rerankStrategyBedrock")}</SelectItem>
                                                    <SelectItem value="VERTEX_AI">{t("globalSettings.rerankStrategyVertexAi")}</SelectItem>
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
                        {(rerankStrategy === "CROSS_ENCODER" || rerankStrategy === "COHERE" || rerankStrategy === "VOYAGE" || rerankStrategy === "BEDROCK" || rerankStrategy === "VERTEX_AI") && (
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
                                                placeholder={rerankStrategy === "COHERE" ? "rerank-v3.5" : rerankStrategy === "VOYAGE" ? "rerank-2.5" : rerankStrategy === "BEDROCK" ? "amazon.rerank-v1:0" : rerankStrategy === "VERTEX_AI" ? "semantic-ranker-default-004" : "BAAI/bge-reranker-v2-m3"}
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
                        {(rerankStrategy === "COHERE" || rerankStrategy === "VOYAGE" || rerankStrategy === "VERTEX_AI") && (
                            <FormField
                                control={form.control}
                                name="ragSnRerankApiKey"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("globalSettings.rerankApiKey")}</FormLabel>
                                        <FormDescription>
                                            {rerankApiKeySet
                                                ? t("globalSettings.rerankApiKeySetDesc")
                                                : (rerankStrategy === "VERTEX_AI"
                                                    ? t("globalSettings.rerankVertexCredentialsDesc")
                                                    : t("globalSettings.rerankApiKeyDesc"))}
                                        </FormDescription>
                                        <FormControl>
                                            <Input
                                                type="password"
                                                className="max-w-md font-mono text-sm"
                                                placeholder={rerankApiKeySet ? "••••••••" : (rerankStrategy === "VERTEX_AI" ? '{"type":"service_account",...}' : "co-...")}
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
                        {rerankStrategy === "BEDROCK" && (
                            <FormField
                                control={form.control}
                                name="ragSnRerankRegion"
                                render={({ field }) => (
                                    <FormItem>
                                        <FormLabel>{t("globalSettings.rerankRegion")}</FormLabel>
                                        <FormDescription>
                                            {t("globalSettings.rerankRegionDesc")}
                                        </FormDescription>
                                        <FormControl>
                                            <Input
                                                className="max-w-md font-mono text-sm"
                                                placeholder="us-east-1"
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
                        {rerankStrategy === "VERTEX_AI" && (
                            <>
                                <FormField
                                    control={form.control}
                                    name="ragSnRerankVertexProject"
                                    render={({ field }) => (
                                        <FormItem>
                                            <FormLabel>{t("globalSettings.rerankVertexProject")}</FormLabel>
                                            <FormDescription>
                                                {t("globalSettings.rerankVertexProjectDesc")}
                                            </FormDescription>
                                            <FormControl>
                                                <Input
                                                    className="max-w-md font-mono text-sm"
                                                    placeholder="my-gcp-project"
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
                                    name="ragSnRerankVertexLocation"
                                    render={({ field }) => (
                                        <FormItem>
                                            <FormLabel>{t("globalSettings.rerankVertexLocation")}</FormLabel>
                                            <FormDescription>
                                                {t("globalSettings.rerankVertexLocationDesc")}
                                            </FormDescription>
                                            <FormControl>
                                                <Input
                                                    className="max-w-md font-mono text-sm"
                                                    placeholder="global"
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
                            </>
                        )}
                        {/* T341 — opt-in rerank-score cache. Applies to every
                            strategy; default off (the win is precision, not
                            latency — enable only on repeated identical calls). */}
                        <FormField
                            control={form.control}
                            name="ragSnRerankCacheEnabled"
                            render={({ field }) => (
                                <FormItemTwoColumns>
                                    <FormItemTwoColumns.Left>
                                        <FormItemTwoColumns.Label>{t("globalSettings.rerankCacheEnable")}</FormItemTwoColumns.Label>
                                        <FormItemTwoColumns.Description>
                                            {t("globalSettings.rerankCacheEnableDesc")}
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
