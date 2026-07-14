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
import { IconWorldWww } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type { GlobalSettingsFormState } from "../use-global-settings-form";

/**
 * T739 / §XLVIII — Content Ingestion: how a content URL is turned into text.
 * `SIMPLE` (legacy default) is the plain HTTP+Tika path; a JS-rendered SPA
 * yields no body text. `HEADLESS` always renders the page in a `browserless`
 * sidecar; `AUTO` runs `SIMPLE` and escalates to `HEADLESS` only when too little
 * text is extracted. Modeled on {@link TranscriptionSection}: a mode dropdown
 * whose sidecar fields appear conditionally, plus a "Check browserless" probe.
 * The same settings are accepted as `turing.url-fetch.*` env/props for
 * headless / Viglet Cloud deploys.
 *
 * @since 2026.3.4
 */
export function ContentIngestionSection({ state }: { state: GlobalSettingsFormState }) {
    const { t } = useTranslation();
    const {
        form,
        isLoading,
        settings,
        browserlessStatus,
        isCheckingBrowserless,
        onCheckBrowserless,
    } = state;
    const mode = form.watch("urlFetchMode") ?? "SIMPLE";
    const usesSidecar = mode === "HEADLESS" || mode === "AUTO";
    const tokenSet = settings.urlFetchBrowserlessTokenSet ?? false;

    return (
        <SectionCard variant="blue">
            <SectionCard.Header
                icon={IconWorldWww}
                title={t("globalSettings.urlFetchTitle")}
                description={t("globalSettings.urlFetchDesc")}
            />
            <SectionCard.Content>
                <FormField
                    control={form.control}
                    name="urlFetchMode"
                    render={({ field }) => (
                        <FormItem>
                            <FormLabel>{t("globalSettings.urlFetchMode")}</FormLabel>
                            <FormDescription>
                                {t("globalSettings.urlFetchModeDesc")}
                            </FormDescription>
                            <FormControl>
                                <Select
                                    onValueChange={field.onChange}
                                    value={field.value ?? "SIMPLE"}
                                    disabled={isLoading}
                                >
                                    <SelectTrigger className="w-full max-w-xs">
                                        <SelectValue placeholder={t("globalSettings.choose")} />
                                    </SelectTrigger>
                                    <SelectContent>
                                        <SelectItem value="SIMPLE">{t("globalSettings.urlFetchModeSimple")}</SelectItem>
                                        <SelectItem value="AUTO">{t("globalSettings.urlFetchModeAuto")}</SelectItem>
                                        <SelectItem value="HEADLESS">{t("globalSettings.urlFetchModeHeadless")}</SelectItem>
                                    </SelectContent>
                                </Select>
                            </FormControl>
                            <FormMessage />
                        </FormItem>
                    )}
                />
                {usesSidecar && (
                    <FormField
                        control={form.control}
                        name="urlFetchBrowserlessUrl"
                        render={({ field }) => (
                            <FormItem>
                                <FormLabel>{t("globalSettings.urlFetchBrowserlessUrl")}</FormLabel>
                                <FormDescription>
                                    {t("globalSettings.urlFetchBrowserlessUrlDesc")}
                                </FormDescription>
                                <FormControl>
                                    <Input
                                        className="max-w-md font-mono text-sm"
                                        placeholder="http://browserless:3000"
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
                {usesSidecar && (
                    <FormField
                        control={form.control}
                        name="urlFetchBrowserlessToken"
                        render={({ field }) => (
                            <FormItem>
                                <FormLabel>{t("globalSettings.urlFetchBrowserlessToken")}</FormLabel>
                                <FormDescription>
                                    {tokenSet
                                        ? t("globalSettings.urlFetchBrowserlessTokenSetDesc")
                                        : t("globalSettings.urlFetchBrowserlessTokenDesc")}
                                </FormDescription>
                                <FormControl>
                                    <Input
                                        type="password"
                                        className="max-w-md font-mono text-sm"
                                        placeholder={tokenSet ? "••••••••" : "token"}
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
                {usesSidecar && (
                    <div className="flex flex-col gap-2">
                        <div className="flex items-center gap-3">
                            <Button
                                type="button"
                                variant="outline"
                                size="sm"
                                onClick={onCheckBrowserless}
                                disabled={isCheckingBrowserless}
                            >
                                {isCheckingBrowserless
                                    ? t("globalSettings.browserlessChecking")
                                    : t("globalSettings.browserlessCheck")}
                            </Button>
                            {browserlessStatus && (
                                <span
                                    className={
                                        browserlessStatus.available
                                            ? "text-sm text-emerald-600 dark:text-emerald-400"
                                            : "text-sm text-amber-600 dark:text-amber-400"
                                    }
                                >
                                    {browserlessStatus.available
                                        ? t("globalSettings.browserlessAvailable", {
                                              version: browserlessStatus.version ?? "",
                                          })
                                        : t("globalSettings.browserlessUnavailable", {
                                              error: browserlessStatus.error ?? "",
                                          })}
                                </span>
                            )}
                        </div>
                        <p className="text-sm text-muted-foreground">
                            {t("globalSettings.urlFetchBrowserlessHint")}
                        </p>
                    </div>
                )}
            </SectionCard.Content>
        </SectionCard>
    );
}
