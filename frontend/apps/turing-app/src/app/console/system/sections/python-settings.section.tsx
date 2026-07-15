import { SectionCard } from "@/components/ui/section-card";
import {
    FormControl,
    FormDescription,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from "@/components/ui/form";
import { GradientButton } from "@/components/ui/gradient-button";
import { Input } from "@/components/ui/input";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { IconAlertTriangle, IconBrandDocker, IconCheck, IconCode } from "@tabler/icons-react";
import { useTranslation } from "react-i18next";
import type { GlobalSettingsFormState } from "../use-global-settings-form";

/**
 * Python / Code Interpreter runtime configuration (interpreter path,
 * auto-installed requirements, native vs. Docker execution mode + image and a
 * Docker availability probe). Part of the "Generative AI" admin page.
 *
 * @since 2026.3.4
 */
export function PythonSettingsSection({ state }: { state: GlobalSettingsFormState }) {
    const { t } = useTranslation();
    const { form, isLoading, dockerStatus, isCheckingDocker, onCheckDocker } = state;
    const codeInterpreterMode = form.watch("codeInterpreterExecutionMode") ?? "NATIVE";

    return (
        <SectionCard variant="amber">
            <SectionCard.Header icon={IconCode} title={t("globalSettings.pythonSectionTitle")} description={t("globalSettings.pythonSectionDesc")} />
            <SectionCard.Content>
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

                {/* Docker-only options — the sandbox image + skill image.
                    Shown only when the mode is DOCKER to keep the NATIVE form
                    uncluttered. The availability probe below is intentionally
                    OUTSIDE this block (see next comment). */}
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
                    </>
                )}

                {/* Docker availability probe — ALWAYS visible as a pre-flight
                    check, even in NATIVE mode, so an admin can confirm the host
                    can run containers BEFORE switching to DOCKER. Keeping it
                    inside the DOCKER-only block created a chicken-and-egg: the
                    button only appeared after committing the mode change, so you
                    couldn't validate the environment ahead of time. In NATIVE
                    mode it reads purely as an informational "can I use DOCKER?"
                    check. */}
                <div className="flex flex-col gap-2">
                    {codeInterpreterMode !== "DOCKER" && (
                        <FormDescription>
                            {t("globalSettings.dockerCheckPreflightHint")}
                        </FormDescription>
                    )}
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
            </SectionCard.Content>
        </SectionCard>
    );
}
