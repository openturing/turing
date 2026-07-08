"use client"
import { ROUTES } from "@/app/routes.const"
import { Form } from "@/components/ui/form"
import { StickyPageHeader } from "@/components/sticky-page-header"
import { BentoHero, BentoScrollSaveBar } from "@/components/bento"
import type {
    TurSNSiteSearchRule,
    TurSNSiteSearchRuleFieldOption,
} from "@/models/sn/sn-site-search-rule.model"
import type { TurSNSiteCustomSort } from "@/models/sn/sn-site-custom-sort.model"
import type { TurSNSiteLocale } from "@/models/sn/sn-site-locale.model"
import type { TurSNSiteFacetOrdering } from "@/models/sn/sn-site-facet-ordering.model"
import { TurSNSiteSearchRuleService } from "@/services/sn/sn.site.search.rule.service"
import { TurSNSiteCustomSortService } from "@/services/sn/sn.site.custom.sort.service"
import { TurSNSiteLocaleService } from "@/services/sn/sn.site.locale.service"
import { TurSNFacetedFieldService } from "@/services/sn/sn.faceted.field.service"
import React, { useEffect, useState } from "react"
import { IconDeviceFloppy, IconGavel, IconX } from "@tabler/icons-react"
import { GradientButton } from "@/components/ui/gradient-button"
import { DialogDelete } from "@/components/dialog.delete"
import { useForm } from "react-hook-form"
import { useTranslation } from "react-i18next"
import { Link, useNavigate } from "react-router-dom"
import { toast } from "@viglet/viglet-design-system"
import { SNSiteSearchRuleDetails } from "./sn.site.search.rule.details"
import { SNSiteSearchRuleConditions } from "./sn.site.search.rule.conditions"
import { SNSiteSearchRuleActions } from "./sn.site.search.rule.actions"
import type { SNSectionChrome } from "./sn.site.search.rule.section"

const turSNSiteSearchRuleService = new TurSNSiteSearchRuleService();
const turSNSiteCustomSortService = new TurSNSiteCustomSortService();
const turSNSiteLocaleService = new TurSNSiteLocaleService();
const turSNFacetedFieldService = new TurSNFacetedFieldService();

interface Props {
    snSiteId: string;
    value: TurSNSiteSearchRule;
    isNew: boolean;
    onDelete?: () => void;
    open?: boolean;
    setOpen?: React.Dispatch<React.SetStateAction<boolean>>;
    /** SN instance base route for save/cancel navigation. Defaults to the
     *  console; the Bento surface passes `ROUTES.BENTO_SN_INSTANCE` (T576). */
    baseRoute?: string;
    /** Render chrome. `console` = StickyPageHeader + SectionCards; `bento` =
     *  BentoHero + frosted BentoFormSection cards (T576). Defaults to console. */
    chrome?: SNSectionChrome;
}

export const SNSiteSearchRuleForm: React.FC<Props> = ({ snSiteId, value, isNew, onDelete, open, setOpen, baseRoute = ROUTES.SN_INSTANCE, chrome = "console" }) => {
    const { t } = useTranslation();
    const form = useForm<TurSNSiteSearchRule>({ defaultValues: value });
    const navigate = useNavigate();
    const urlBase = `${baseRoute}/${snSiteId}/search-rule`;
    const [fieldOptions, setFieldOptions] = useState<TurSNSiteSearchRuleFieldOption[]>([]);
    const [customSorts, setCustomSorts] = useState<TurSNSiteCustomSort[]>([]);
    const [siteLocales, setSiteLocales] = useState<TurSNSiteLocale[]>([]);
    const [facets, setFacets] = useState<TurSNSiteFacetOrdering[]>([]);

    useEffect(() => {
        form.reset(value);
    }, [value]);

    useEffect(() => {
        turSNSiteSearchRuleService.getFieldOptions(snSiteId).then(setFieldOptions).catch(console.error);
        turSNSiteCustomSortService.query(snSiteId).then(setCustomSorts).catch(console.error);
        turSNSiteLocaleService.query(snSiteId).then(setSiteLocales).catch(console.error);
        turSNFacetedFieldService.query(snSiteId).then(setFacets).catch(console.error);
    }, [snSiteId]);

    async function onSubmit(data: TurSNSiteSearchRule) {
        try {
            if (isNew) {
                const result = await turSNSiteSearchRuleService.create(snSiteId, data);
                if (result) {
                    toast.success(t("sn.searchRule.saved"));
                    navigate(urlBase);
                } else {
                    toast.error(t("sn.searchRule.notSaved"));
                }
            } else {
                const result = await turSNSiteSearchRuleService.update(snSiteId, data);
                if (result) {
                    toast.success(t("sn.searchRule.updated"));
                } else {
                    toast.error(t("sn.searchRule.notUpdated"));
                }
            }
        } catch (error) {
            console.error("Form submission error", error);
            toast.error(t("forms.common.formSubmitFailed"));
        }
    }

    const isBento = chrome === "bento";

    const actions = (
        <>
            {onDelete && open !== undefined && setOpen && <DialogDelete feature={t("sn.searchRule.title")} name={value?.name || t("sn.searchRule.newSearchRule")} onDelete={onDelete} open={open} setOpen={setOpen} />}
            <GradientButton type="submit" size="sm">
                <IconDeviceFloppy className="size-4" />
                {t("forms.formActions.saveChanges")}
            </GradientButton>
            <GradientButton type="button" variant="outline" size="sm" onClick={() => navigate(urlBase)}>
                <IconX className="size-4" />
                {t("forms.formActions.cancel")}
            </GradientButton>
        </>
    );

    return (
        <Form {...form}>
            <form onSubmit={form.handleSubmit(onSubmit)} className={isBento ? "space-y-5 pb-8" : "space-y-4 px-4 lg:px-6 pb-8"}>
                {isBento ? (
                    <>
                    <BentoHero
                        eyebrow={<Link to={urlBase} className="hover:text-foreground">{t("sn.searchRule.title")}</Link>}
                        leading={
                            <span className="grid h-12 w-12 place-items-center rounded-2xl bg-linear-to-br from-emerald-500 to-teal-600 text-white shadow-md">
                                <IconGavel size={24} />
                            </span>
                        }
                        title={value?.name || t("sn.searchRule.newSearchRule")}
                        subtitle={t("sn.searchRule.description")}
                        trailing={<div className="bento-fade-out flex shrink-0 items-center gap-2">{actions}</div>}
                    />
                    <BentoScrollSaveBar onCancel={() => navigate(urlBase)} />
                    </>
                ) : (
                    <StickyPageHeader>
                        <StickyPageHeader.Title
                            icon={IconGavel}
                            feature={t("sn.searchRule.title")}
                            description={t("sn.searchRule.description")}
                        />
                        <StickyPageHeader.Actions>{actions}</StickyPageHeader.Actions>
                    </StickyPageHeader>
                )}
                <SNSiteSearchRuleDetails form={form} chrome={chrome} />
                <SNSiteSearchRuleConditions
                    form={form}
                    fieldOptions={fieldOptions}
                    customSorts={customSorts}
                    siteLocales={siteLocales}
                    chrome={chrome}
                />
                <SNSiteSearchRuleActions
                    form={form}
                    fieldOptions={fieldOptions}
                    customSorts={customSorts}
                    facets={facets}
                    chrome={chrome}
                />
            </form>
        </Form>
    );
};
