"use client"
import {
    FormControl,
    FormDescription,
    FormField,
    FormItem,
    FormLabel,
    FormMessage,
} from "@/components/ui/form"
import { GradientSwitch } from "@/components/ui/gradient-switch"
import { Input } from "@/components/ui/input"
import { SmartDescription } from "@/components/ui/smart-description"
import type { TurSNSiteSearchRule } from "@/models/sn/sn-site-search-rule.model"
import { IconGavel } from "@tabler/icons-react"
import React from "react"
import { useTranslation } from "react-i18next"
import type { UseFormReturn } from "react-hook-form"
import { SNSiteSearchRuleSection, type SNSectionChrome } from "./sn.site.search.rule.section"

interface Props {
    form: UseFormReturn<TurSNSiteSearchRule>;
    chrome?: SNSectionChrome;
}

export const SNSiteSearchRuleDetails: React.FC<Props> = ({ form, chrome = "console" }) => {
    const { t } = useTranslation();

    return (
        <SNSiteSearchRuleSection chrome={chrome} icon={IconGavel} tone="blue" title={t("sn.searchRule.details")} description={t("sn.searchRule.detailsDesc")}>
                <FormField
                    control={form.control}
                    name="name"
                    rules={{ required: t("sn.searchRule.nameRequired") }}
                    render={({ field }) => (
                        <FormItem>
                            <FormLabel>{t("forms.common.name")}</FormLabel>
                            <FormDescription>{t("sn.searchRule.nameDesc")}</FormDescription>
                            <FormControl>
                                <Input {...field} placeholder="e.g. browse_education" type="text" />
                            </FormControl>
                            <FormMessage />
                        </FormItem>
                    )}
                />
                <FormField
                    control={form.control}
                    name="description"
                    render={({ field }) => (
                        <FormItem>
                            <FormControl>
                                <SmartDescription
                                    value={field.value || ""} onChange={field.onChange}
                                    placeholder={t("sn.searchRule.descriptionPlaceholder")}
                                    maxLength={500} entityType="Search Rule" enableMetaPrompt
                                >
                                    <SmartDescription.Label>{t("forms.common.description")}</SmartDescription.Label>
                                    <SmartDescription.Description>{t("sn.searchRule.descriptionDesc")}</SmartDescription.Description>
                                </SmartDescription>
                            </FormControl>
                            <FormMessage />
                        </FormItem>
                    )}
                />
                <div className="grid grid-cols-2 gap-4">
                    <FormField
                        control={form.control}
                        name="position"
                        render={({ field }) => (
                            <FormItem>
                                <FormLabel>{t("sn.searchRule.position")}</FormLabel>
                                <FormDescription>{t("sn.searchRule.positionDesc")}</FormDescription>
                                <FormControl>
                                    <Input {...field} type="number" min={0}
                                        onChange={(e) => field.onChange(Number.parseInt(e.target.value) || 0)} />
                                </FormControl>
                                <FormMessage />
                            </FormItem>
                        )}
                    />
                    <FormField
                        control={form.control}
                        name="enabled"
                        render={({ field }) => (
                            <FormItem className="flex flex-col">
                                <FormLabel>{t("sn.searchRule.enabled")}</FormLabel>
                                <FormDescription>{t("sn.searchRule.enabledDesc")}</FormDescription>
                                <FormControl>
                                    <GradientSwitch checked={field.value} onCheckedChange={field.onChange} />
                                </FormControl>
                            </FormItem>
                        )}
                    />
                </div>
        </SNSiteSearchRuleSection>
    );
};
