import { SectionCard } from "@/components/ui/section-card";
import { FormControl, FormField, FormMessage } from "@/components/ui/form";
import { FormItemTwoColumns } from "@/components/ui/form-item-two-columns";
import { GradientSwitch } from "@/components/ui/gradient-switch";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { IconDatabase } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type { GlobalSettingsFormState } from "../use-global-settings-form";

/**
 * Global RAG toggle plus default embedding model / store selection. Part of the
 * "Generative AI" admin page.
 *
 * @since 2026.3.4
 */
export function RagSettingsSection({ state }: { state: GlobalSettingsFormState }) {
    const { t } = useTranslation();
    const { form, isLoading, embeddingModels, storeInstances } = state;
    const ragEnabled = form.watch("ragEnabled") ?? false;

    return (
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
    );
}
